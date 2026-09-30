package net.muxigame.core.threading.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.*;
import net.muxigame.core.threading.ChunkTravel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TravelAdmissionMixin {
    @Shadow public ServerPlayer player;
    // This point is after thread dispatch, finite-value validation and pending teleport
    // handling, but before collision reads. Accepted packets run all vanilla checks.
    @Inject(method="handleMovePlayer",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampHorizontal(D)D",ordinal=0),cancellable=true)
    private void muxi$player(ServerboundMovePlayerPacket packet,CallbackInfo ci) {
        if(player.isPassenger() || player.isSleeping())return;
        if(!ChunkTravel.allow(player,player,packet.getX(player.getX()),packet.getY(player.getY()),packet.getZ(player.getZ()))) {
            player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
            ci.cancel();
        }
    }
    @Inject(method="handleMoveVehicle",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampHorizontal(D)D",ordinal=0),cancellable=true)
    private void muxi$vehicle(ServerboundMoveVehiclePacket packet,CallbackInfo ci) {
        var vehicle=player.getRootVehicle();
        if(!ChunkTravel.allow(player,vehicle,packet.getX(),packet.getY(),packet.getZ())) {
            player.connection.send(new ClientboundMoveVehiclePacket(vehicle));ci.cancel();
        }
    }
}
