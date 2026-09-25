package net.muxigame.core.feature.chat;

import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.nickname.Nicknames;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 普通聊天（不是指令）按 Tab：原版只补在线玩家的登录名，也就是 UID，补进聊天里的也是 UID。
 *
 * <p>原版留了个口子，服务端可以用 {@link ClientboundCustomChatCompletionsPacket} 往客户端的补全表里加词；
 * 这里把在线玩家的昵称放进去。聊天里插的就是昵称本身——那是给人看的文字，不是身份。
 * 只用原版的包，客户端装不装本模组都生效；UID 也还在补全表里（那部分是客户端自己从玩家列表取的）。
 */
public final class ChatCompletionFeature implements ServerFeature {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/chat");
    /** 昵称由 IdentityFeature 异步同步、管理员也可能改，没有事件可挂，隔 2 秒对一次账。 */
    private static final int REFRESH_TICKS = 40;
    /** 已经告诉所有在线客户端的昵称；新进来的客户端整张收一次，之后大家只收增减。 */
    private Set<String> announced = Set.of();
    private long ticks;

    @Override public String id() { return "chat-completion"; }

    @Override public void register(IEventBus gameBus) {
        gameBus.addListener(this::onLogin);
        gameBus.addListener(this::onLogout);
        gameBus.addListener(this::onTick);
        LOG.info("Plain chat Tab completion offers online players' nicknames");
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        refresh(player.server, null);
        player.connection.send(new ClientboundCustomChatCompletionsPacket(
            ClientboundCustomChatCompletionsPacket.Action.SET, List.copyOf(announced)));
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 事件触发时他还在在线列表里，得排除掉再算，不然他的昵称要等下一轮才从别人的补全里消失。
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) refresh(player.server, player);
    }

    private void onTick(ServerTickEvent.Post event) {
        if (++ticks % REFRESH_TICKS == 0) refresh(event.getServer(), null);
    }

    private void refresh(MinecraftServer server, ServerPlayer leaving) {
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        List<String> logins = new ArrayList<>(players.size());
        for (ServerPlayer player : players) if (player != leaving) logins.add(player.getGameProfile().getName());
        // Simple Nicknames 不可用时 Nicknames.of 一律返回 null：集合变空，已经发出去的词随之撤回。
        Set<String> now = CompletionDiff.nicknames(logins, Nicknames::of);
        List<String> added = CompletionDiff.added(announced, now);
        List<String> removed = CompletionDiff.removed(announced, now);
        announced = now;
        if (added.isEmpty() && removed.isEmpty()) return;
        for (ServerPlayer player : players) {
            if (player == leaving || player instanceof FakePlayer) continue;
            if (!removed.isEmpty())
                player.connection.send(new ClientboundCustomChatCompletionsPacket(ClientboundCustomChatCompletionsPacket.Action.REMOVE, removed));
            if (!added.isEmpty())
                player.connection.send(new ClientboundCustomChatCompletionsPacket(ClientboundCustomChatCompletionsPacket.Action.ADD, added));
        }
    }

    @Override public void close() {
        announced = Set.of();
        ticks = 0;
    }
}
