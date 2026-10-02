package net.muxigame.transferprobe.mixin;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.transferprobe.ProbeWindow;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerAckMixin {
    @Inject(method = "handleAcceptTeleportPacket", at = @At("RETURN"))
    private void probe$ack(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        ProbeWindow w = Trace.server(this);
        if (w != null && w.teleportId == packet.getId()) Trace.event("server", w, "teleport_ack", true, null, null);
    }
}
