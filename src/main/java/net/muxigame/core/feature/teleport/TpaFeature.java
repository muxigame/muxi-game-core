package net.muxigame.core.feature.teleport;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.ServerFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consent-based player teleport requests for ordinary players.
 * This deliberately does not grant access to vanilla /tp.
 */
public final class TpaFeature implements ServerFeature {
    private static final long TIMEOUT_MS = 60_000L;
    private final Map<UUID, Request> pendingByTarget = new HashMap<>();

    @Override public String id() { return "tpa"; }

    @Override public void register(IEventBus gameBus) {
        gameBus.addListener(this::commands);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tpa")
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> request(
                    ctx.getSource().getPlayerOrException(),
                    EntityArgument.getPlayer(ctx, "player")))));

        event.getDispatcher().register(Commands.literal("tpaccept")
            .executes(ctx -> accept(ctx.getSource().getPlayerOrException())));

        event.getDispatcher().register(Commands.literal("tpdeny")
            .executes(ctx -> deny(ctx.getSource().getPlayerOrException())));

        event.getDispatcher().register(Commands.literal("tpcancel")
            .executes(ctx -> cancel(ctx.getSource().getPlayerOrException())));
    }

    private int request(ServerPlayer requester, ServerPlayer target) {
        if (requester == target) {
            tell(requester, "不能向自己发送传送请求。");
            return 0;
        }
        pruneExpired();
        Request existing = pendingByTarget.get(target.getUUID());
        if (existing != null && !existing.requester.equals(requester.getUUID())) {
            tell(requester, "对方当前已有待处理的传送请求，请稍后再试。");
            return 0;
        }

        pendingByTarget.put(target.getUUID(), new Request(requester.getUUID(), System.currentTimeMillis() + TIMEOUT_MS));
        tell(requester, "已向 " + target.getScoreboardName() + " 发送传送请求，60 秒内有效。");

        Component accept = Component.literal("[接受]")
            .withStyle(s -> s.withColor(0x76E39A)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept")));
        Component deny = Component.literal("[拒绝]")
            .withStyle(s -> s.withColor(0xFF7B7B)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny")));

        target.sendSystemMessage(Component.literal(requester.getScoreboardName() + " 请求传送到你身边。 ")
            .append(accept).append(Component.literal(" ")).append(deny));
        target.sendSystemMessage(Component.literal("请求将在 60 秒后失效。"));
        return 1;
    }

    private int accept(ServerPlayer target) {
        pruneExpired();
        Request request = pendingByTarget.remove(target.getUUID());
        if (request == null) {
            tell(target, "当前没有待处理的传送请求。");
            return 0;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requester);
        if (requester == null) {
            tell(target, "请求者已经离线，传送请求已失效。");
            return 0;
        }

        requester.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        tell(requester, target.getScoreboardName() + " 已接受你的传送请求。");
        tell(target, "已接受 " + requester.getScoreboardName() + " 的传送请求。");
        return 1;
    }

    private int deny(ServerPlayer target) {
        pruneExpired();
        Request request = pendingByTarget.remove(target.getUUID());
        if (request == null) {
            tell(target, "当前没有待处理的传送请求。");
            return 0;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requester);
        if (requester != null) tell(requester, target.getScoreboardName() + " 拒绝了你的传送请求。");
        tell(target, "已拒绝传送请求。");
        return 1;
    }

    private int cancel(ServerPlayer requester) {
        pruneExpired();
        UUID targetId = pendingByTarget.entrySet().stream()
            .filter(e -> e.getValue().requester.equals(requester.getUUID()))
            .map(Map.Entry::getKey).findFirst().orElse(null);
        if (targetId == null) {
            tell(requester, "你当前没有待处理的传送请求。");
            return 0;
        }
        pendingByTarget.remove(targetId);
        ServerPlayer target = requester.server.getPlayerList().getPlayer(targetId);
        if (target != null) tell(target, requester.getScoreboardName() + " 已取消传送请求。");
        tell(requester, "已取消传送请求。");
        return 1;
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        pendingByTarget.values().removeIf(r -> r.expiresAt <= now);
    }

    private static void tell(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }

    @Override public void close() { pendingByTarget.clear(); }

    private record Request(UUID requester, long expiresAt) {}
}
