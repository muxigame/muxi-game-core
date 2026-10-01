package net.muxigame.core.feature.login;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.identity.IdentityRules;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerNegotiationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 进服凭据核验。
 *
 * <p>这台服务器是 {@code online-mode=false}：Minecraft 自己一个字节的身份校验都不做，
 * 客户端在握手里报什么用户名，服务端就认什么。我们的用户名是平台 UID，而 UID 是从
 * 10000 开始的顺号——把用户名填成别人的 UID，就是别人。换个"不好猜"的登录名也救不了：
 * 游戏内 {@code /msg} 的 Tab 补全会把所有在线玩家的登录名列出来，名字从来就不是秘密。
 *
 * <p>所以这里不再问"你叫什么"，而是问平台"这个 UID 刚刚有人拿本人的账号换过票吗"。
 * 票由启动器在玩家真正发起连接的那一刻换走，一次性，几分钟就过期。冒名者拿得到 UID，
 * 拿不到票。
 *
 * <p><b>失败一律拒绝。</b>平台超时、502、连不上、密钥不对——全部按"不放行"处理。
 * 这是有意选的：核验形同虚设的时候放人进来，等于这个功能在最需要它的时候不存在，
 * 而且没有任何人会发现。代价是 muxi-auth 一挂全服就进不来，运维上用
 * {@code features.login.enabled=false} 手动放行。
 *
 * <p>拒绝走两道：negotiation 阶段断连，以及万一那一步没拦住，在
 * {@link PlayerEvent.PlayerLoggedInEvent} 再踢一次。第二道是兜底——第一道要是在某个
 * NeoForge 版本上不生效，这个功能会**静默失效**，而不是报错，那是安全控制最糟的失败
 * 方式。多这十几行，失效就变成"进来一下又被踢"，看得见。
 */
public final class LoginGate implements ServerFeature {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/login");

    /** 兜底名单的保留时长：握手到进世界之间的窗口，留得远比实际需要宽。 */
    private static final long ADMISSION_TTL_MS = 5 * 60 * 1000L;

    private static final Component NO_GRANT = Component.literal(
        "请通过 muxi 启动器进入游戏。\n直接用游戏客户端连接不再被接受。");
    private static final Component UNKNOWN_UID = Component.literal(
        "未找到该 UID，请先创建并登录 muxi 账户。");
    private static final Component UNAVAILABLE = Component.literal(
        "账户服务暂时不可用，进服校验没能完成。请稍后再试。");

    private final HttpClient http;
    private final CoreConfig.Login config;
    /** 已经核验通过、但还没走完登录流程的人。值是 monotonic 毫秒，用来清扫掉队的条目。 */
    private final Map<UUID, Long> admitted = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    public LoginGate(CoreConfig.Login config) {
        this.config = config;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public String id() { return "login"; }

    @Override public void register(IEventBus gameBus) {
        gameBus.addListener(this::onNegotiate);
        gameBus.addListener(this::onLoggedIn);
        LOG.info("Join-grant verification enabled; unverified logins are refused (timeout {}s)", config.timeoutSeconds());
    }

    /**
     * NeoForge 会一直等到这里交回去的 future 完成才让登录往下走，所以核验可以是异步的，
     * 玩家那边看到的是"正在登录"而不是被打断。
     */
    private void onNegotiate(PlayerNegotiationEvent event) {
        Connection connection = event.getConnection();
        String uid = event.getProfile().getName();
        UUID id = event.getProfile().getId();

        if (stopped) { refuse(connection, UNAVAILABLE, uid, "feature stopped"); return; }
        if (!IdentityRules.validUid(uid)) {
            refuse(connection, Component.literal("请使用最新版 muxi 启动器。游戏登录身份为平台 UID。"), uid, "not a UID");
            return;
        }
        // UUID 必须是这个 UID 算出来的那一个。对不上说明客户端在自造身份，
        // 而存档是按 UUID 存的——放过去就是拿着别人的 UUID 开别人的背包。
        if (id == null || !id.equals(IdentityRules.offlineUuid(uid))) {
            refuse(connection, Component.literal("游戏 UUID 与平台 UID 不匹配。"), uid, "UUID mismatch");
            return;
        }
        event.enqueueWork(verify(connection, uid, id));
    }

    private CompletableFuture<Void> verify(Connection connection, String uid, UUID id) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.endpoint() + uid))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Accept", "application/json")
                .header("X-Muxi-Server-Key", config.serverKey())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        } catch (RuntimeException malformed) {
            // URI 里不该出现玩家可控的内容——validUid 已经把 uid 限成纯数字——
            // 真走到这里是配置坏了，不是玩家的问题，但一样不放行。
            refuse(connection, UNAVAILABLE, uid, "cannot build request");
            gate.complete(null);
            return gate;
        }
        try {
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(config.timeoutSeconds() + 2L, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    try { decide(connection, uid, id, response, error); }
                    catch (RuntimeException unexpected) { refuse(connection, UNAVAILABLE, uid, "verifier crashed"); }
                    // 无论如何都要完成：future 不完成，玩家就永远卡在登录里。
                    finally { gate.complete(null); }
                });
        } catch (RuntimeException rejected) {
            refuse(connection, UNAVAILABLE, uid, "cannot schedule lookup");
            gate.complete(null);
        }
        return gate;
    }

    private void decide(Connection connection, String uid, UUID id, HttpResponse<String> response, Throwable error) {
        if (error != null) { refuse(connection, UNAVAILABLE, uid, "lookup failed: " + error.getClass().getSimpleName()); return; }
        switch (response.statusCode()) {
            case 200 -> {
                sweep();
                admitted.put(id, System.currentTimeMillis());
                LOG.info("UID {} presented a valid join grant", uid);
            }
            // 409：UID 存在，但没有票。这正是冒名的样子，单独记一条。
            case 409 -> refuse(connection, NO_GRANT, uid, "no join grant (impersonation attempt or stale launcher)");
            case 404 -> refuse(connection, UNKNOWN_UID, uid, "unknown UID");
            default -> refuse(connection, UNAVAILABLE, uid, "unexpected HTTP " + response.statusCode());
        }
    }

    /** 兜底：negotiation 那一步万一没拦住，进世界的瞬间再核一次名单。 */
    private void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (admitted.remove(player.getUUID()) != null) { net.muxigame.minigames.TrustedAccounts.admit(player); return; }
        LOG.warn("UID {} reached login without a verified join grant; disconnecting late",
            player.getGameProfile().getName());
        player.connection.disconnect(NO_GRANT);
    }

    private void refuse(Connection connection, Component reason, String uid, String detail) {
        LOG.warn("Refused login for UID {}: {}", uid, detail);
        try { if (connection.isConnected()) connection.disconnect(reason); }
        catch (RuntimeException ignored) { LOG.warn("Could not disconnect refused UID {}", uid); }
    }

    private void sweep() {
        long deadline = System.currentTimeMillis() - ADMISSION_TTL_MS;
        admitted.entrySet().removeIf(entry -> entry.getValue() < deadline);
    }

    @Override public void close() {
        stopped = true;
        http.shutdownNow();
        admitted.clear();
    }
}
