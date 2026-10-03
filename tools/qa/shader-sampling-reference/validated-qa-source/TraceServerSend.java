package net.muxigame.binaryqa.mixin;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;import net.minecraft.network.protocol.Packet;import net.minecraft.network.PacketSendListener;import net.muxigame.binaryqa.NativeProbe;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class) public class TraceServerSend {
 @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",at=@At("HEAD")) private void send(Packet<?> p,PacketSendListener listener,CallbackInfo ci){NativeProbe.packet(p);}
}
