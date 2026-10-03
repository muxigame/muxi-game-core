package net.muxigame.binaryqa.mixin;
import net.minecraft.client.renderer.LevelRenderer;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class)
public class TraceFirstDraw {
 @Inject(method="renderLevel",at=@At("HEAD")) private void qa$begin(CallbackInfo ci){Trace.begin("worldDraw");}
 @Inject(method="renderLevel",at=@At("RETURN")) private void qa$end(CallbackInfo ci){Trace.end("worldDraw");}
}
