package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="net.irisshaders.iris.pipeline.PipelineManager",remap=false)
public class PipelineDimensionShaderMixin {
    @Inject(method="preparePipeline",at=@At("HEAD"),remap=false,require=0)
    private void muxi$prepare(CallbackInfoReturnable<Object> ci){DimensionShaderSwap.beforePipeline();}
}
