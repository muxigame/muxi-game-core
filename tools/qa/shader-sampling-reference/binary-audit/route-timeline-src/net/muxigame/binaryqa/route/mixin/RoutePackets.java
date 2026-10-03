package net.muxigame.binaryqa.route.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.muxigame.binaryqa.route.RouteTimeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public class RoutePackets {
 @Inject(method="handleLevelChunkWithLight(Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;)V",at=@At("HEAD"),require=1)
 private void routeHead(ClientboundLevelChunkWithLightPacket p,CallbackInfo ci){RouteTimeline.packet(p.getX(),p.getZ(),false);}
 @Inject(method="handleLevelChunkWithLight(Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;)V",at=@At("RETURN"),require=1)
 private void routeReturn(ClientboundLevelChunkWithLightPacket p,CallbackInfo ci){RouteTimeline.packet(p.getX(),p.getZ(),true);}
 @Inject(method="handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V",at=@At("RETURN"),require=1)
 private void routePosition(CallbackInfo ci){if(RouteTimeline.clientDimensionMatches()){RouteTimeline.resolvePlayerTarget();RouteTimeline.mark("positionPacketReturn");}}
}
