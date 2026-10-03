package net.muxigame.shadernative.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;import net.muxigame.shadernative.ClientQA;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class JoinLoginBoundary {
 @Inject(method="handleLogin",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",shift=At.Shift.AFTER),require=1,allow=1)
 private void qa$loginThread(net.minecraft.network.protocol.game.ClientboundLoginPacket packet,CallbackInfo ci){ClientQA.loginThreadBoundary(packet.commonPlayerSpawnInfo().dimension().location().toString());}
}
