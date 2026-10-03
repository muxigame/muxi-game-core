package net.muxigame.binaryqa.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public class TraceClientPackets {
 @Inject(method="handleLogin",at=@At("HEAD")) private void qa$loginBegin(CallbackInfo ci){if(net.minecraft.client.Minecraft.getInstance().isSameThread())Trace.begin("clientLogin");}
 @Inject(method="handleLogin",at=@At("RETURN")) private void qa$loginEnd(CallbackInfo ci){if(net.minecraft.client.Minecraft.getInstance().isSameThread())Trace.end("clientLogin");}
 @Inject(method="handleRespawn",at=@At("HEAD")) private void qa$respawnBegin(CallbackInfo ci){if(net.minecraft.client.Minecraft.getInstance().isSameThread())Trace.begin("clientRespawn");}
 @Inject(method="handleRespawn",at=@At("RETURN")) private void qa$respawnEnd(CallbackInfo ci){if(net.minecraft.client.Minecraft.getInstance().isSameThread())Trace.end("clientRespawn");}
}
