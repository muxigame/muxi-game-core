package net.muxigame.core.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's one-player save. Reward claims must not force a save of every online player. */
@Mixin(PlayerList.class)
public interface PlayerListSaveInvoker {
    @Invoker("save") void muxi$savePlayer(ServerPlayer player);
}
