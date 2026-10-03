package net.muxigame.shaderfixqa;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline",remap=false)
public class PipelineConstructionProbe {
 @Inject(method="<init>",at=@At("RETURN"),require=1,remap=false)
 private void shaderfixqa$constructed(CallbackInfo ci){
  if(Trace.ready){long now=System.nanoTime();Trace.add("irisPipelineConstructed",now,now);}
 }
}
