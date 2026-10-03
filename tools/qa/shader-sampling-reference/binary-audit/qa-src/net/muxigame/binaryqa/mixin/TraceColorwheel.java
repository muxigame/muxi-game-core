package net.muxigame.binaryqa.mixin;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="dev.djefrey.colorwheel.compile.ClrwlPipelineCompiler",remap=false)
public class TraceColorwheel {
 @Inject(method="compile",at=@At("HEAD")) private void qa$begin(CallbackInfoReturnable<Object> ci){Trace.begin("colorwheelProgramPrepare");}
 @Inject(method="compile",at=@At("RETURN")) private void qa$end(CallbackInfoReturnable<Object> ci){Trace.end("colorwheelProgramPrepare");}
}
