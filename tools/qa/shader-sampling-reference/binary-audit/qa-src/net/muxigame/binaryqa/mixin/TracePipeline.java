package net.muxigame.binaryqa.mixin;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="net.irisshaders.iris.pipeline.PipelineManager",remap=false)
public class TracePipeline {
 @Inject(method="preparePipeline",at=@At("HEAD")) private void qa$prepareBegin(CallbackInfoReturnable<Object> ci){Trace.begin("pipelinePrepare");}
 @Inject(method="preparePipeline",at=@At("RETURN")) private void qa$prepareEnd(CallbackInfoReturnable<Object> ci){Trace.end("pipelinePrepare");}
 @Inject(method="destroyPipeline",at=@At("HEAD")) private void qa$destroyBegin(CallbackInfo ci){Trace.begin("pipelineDestroy");}
 @Inject(method="destroyPipeline",at=@At("RETURN")) private void qa$destroyEnd(CallbackInfo ci){Trace.end("pipelineDestroy");}
}
