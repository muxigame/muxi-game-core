package net.muxigame.core.feature.identity;

import com.donutello.simplenicknames.SimpleNicknamesMain;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.ServerFeature;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Optional display integration. Public UIDs are not authentication credentials. */
public final class IdentityFeature implements ServerFeature {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/identity");
    private static final Set<String> NICK_COMMANDS = Set.of("nick", "setnick", "randomnick", "unnick");
    private final HttpClient http;
    private final GameOpSync opSync;
    private final CoreConfig.Identity config;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Result> completed = new ConcurrentLinkedQueue<>();
    private long ticks;
    private volatile boolean stopped;
    private record Result(ServerPlayer player, String uid, int status, String body) {}

    public IdentityFeature(CoreConfig.Identity config) { this(config, CoreConfig.disabled().opSync()); }
    public IdentityFeature(CoreConfig.Identity config, CoreConfig.OpSync ops) {
        this.config = config;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
        opSync = ops.enabled() ? new GameOpSync(http, ops) : null;
    }
    @Override public String id() { return "identity"; }
    @Override public void register(IEventBus gameBus) {
        gameBus.addListener(this::onLogin);
        if (opSync != null) gameBus.addListener(opSync::registerCommands);
        gameBus.addListener(this::onTick);
        gameBus.addListener(this::onCommand);
        LOG.info("Platform UID / nickname integration enabled; refresh every {}s", config.refreshSeconds());
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queue(player);
    }
    private void queue(ServerPlayer player) {
        // Applied to login AND periodic refresh, so NPCs never trigger network IO.
        if (player instanceof FakePlayer || stopped) return;
        String uid = player.getGameProfile().getName();
        if (!IdentityRules.validUid(uid)) {
            player.connection.disconnect(Component.literal("请使用最新版 muxi 启动器。游戏登录身份为平台 UID。"));
            return;
        }
        UUID id = player.getUUID();
        if (!id.equals(IdentityRules.offlineUuid(uid))) {
            player.connection.disconnect(Component.literal("游戏 UUID 与平台 UID 不匹配。"));
            return;
        }
        fetch(player, uid);
    }
    private void fetch(ServerPlayer player, String uid) {
        UUID id = player.getUUID();
        if (stopped || pending.size() >= 32 || !pending.add(id)) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint() + uid)).timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json").header("X-Muxi-Server-Key", config.serverKey()).GET().build();
        try {
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> {
                    if (!stopped) completed.add(new Result(player, uid, error == null ? response.statusCode() : 0,
                        error == null && response.body().length() <= 8192 ? response.body() : ""));
                });
        } catch (RuntimeException ignored) {
            pending.remove(id);
            LOG.warn("Could not schedule identity lookup for UID {}", uid);
        }
    }
    private void onTick(ServerTickEvent.Post event) {
        if (stopped || ++ticks % 20 != 0) return;
        MinecraftServer server = event.getServer();
        if (opSync != null) opSync.tick(server, ticks == 20 || ticks % (config.refreshSeconds() * 20L) == 0);
        Result result;
        while ((result = completed.poll()) != null) {
            pending.remove(result.player().getUUID());
            ServerPlayer player = server.getPlayerList().getPlayer(result.player().getUUID());
            // Never apply a previous connection's response to a newly joined player.
            if (player != result.player() || !player.getGameProfile().getName().equals(result.uid())) continue;
            if (result.status() == 404) {
                player.connection.disconnect(Component.literal("未找到该 UID，请先创建并登录 muxi 账户。"));
                continue;
            }
            if (result.status() != 200) {
                LOG.warn("UID {} lookup failed (HTTP {}); existing nickname retained.", result.uid(), result.status());
                continue;
            }
            try {
                JsonObject profile = JsonParser.parseString(result.body()).getAsJsonObject();
                if (!result.uid().equals(profile.get("loginName").getAsString())
                    || !result.uid().equals(profile.get("uid").getAsString())
                    || !player.getUUID().toString().equals(profile.get("offlineUuid").getAsString())) throw new IllegalArgumentException();
                String display = profile.get("displayName").getAsString().strip();
                if (!IdentityRules.validDisplayName(display)) throw new IllegalArgumentException();
                var manager = SimpleNicknamesMain.NICKNAME_MANAGER;
                if (!display.equals(manager.getRawNickname(player))) {
                    manager.setNickname(player, display);
                    LOG.info("Platform nickname synchronized for UID {}", result.uid());
                }
            } catch (Exception ignored) { LOG.warn("Rejected invalid identity response for UID {}", result.uid()); }
        }
        if (ticks % (config.refreshSeconds() * 20L) == 0) server.getPlayerList().getPlayers().forEach(this::queue);
    }
    private void onCommand(CommandEvent event) {
        if (stopped) return;
        var source = event.getParseResults().getContext().getSource();
        if (!source.isPlayer()) return;
        String command = event.getParseResults().getReader().getString().strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        command = command.substring(command.lastIndexOf(':') + 1);
        if (NICK_COMMANDS.contains(command)) {
            source.sendFailure(Component.literal("请在 muxi 账户中心修改昵称，游戏内会自动同步。"));
            event.setCanceled(true);
        }
    }
    @Override public void close() {
        stopped = true;
        if (opSync != null) opSync.close();
        http.shutdownNow();
        pending.clear();
        completed.clear();
    }
}
