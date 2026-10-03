package net.muxigame.shadernative.mixin;
import net.minecraft.client.Minecraft;import net.muxigame.binaryqa.Trace;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class) public class ClientLevelSpan {
 @Inject(method="setLevel",at=@At("HEAD"),require=1) private void begin(CallbackInfo ci){Trace.begin("setLevel");}
 @Inject(method="setLevel",at=@At("RETURN"),require=1) private void end(CallbackInfo ci){Trace.end("setLevel");}
}
