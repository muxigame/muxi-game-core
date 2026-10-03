package net.muxigame.shadernative.mixin;
import net.muxigame.binaryqa.Trace;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false) public class PipelineCreated {
 @Inject(method="<init>",at=@At("RETURN"),require=1) private void created(CallbackInfo ci){long now=System.nanoTime();Trace.add("pipelineCreated",now,now);}
}
