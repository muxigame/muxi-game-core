package net.muxigame.core.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.muxigame.core.feature.challenge.ChallengeInventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary challenge kit cannot be moved into persistent accessory slots or remote containers. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChallengeContainerMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick",at=@At(value="INVOKE",
        target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        shift=At.Shift.AFTER),cancellable=true)
    private void muxi$kitLocked(ServerboundContainerClickPacket packet,CallbackInfo ci) {
        if(ChallengeInventory.pending(player)) {
            ci.cancel();player.containerMenu.sendAllDataToRemote();
        }
    }
}
