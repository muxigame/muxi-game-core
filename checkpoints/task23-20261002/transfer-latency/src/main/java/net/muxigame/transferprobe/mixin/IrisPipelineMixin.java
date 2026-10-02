package net.muxigame.transferprobe.mixin;

import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public class IrisPipelineMixin {
    @Inject(method = "preparePipeline", at = @At("HEAD"), remap = false)
    private void probe$begin(CallbackInfoReturnable<Object> ci) { Trace.event("client", Trace.client(), "iris_pipeline_begin", false, null, null); }
    @Inject(method = "preparePipeline", at = @At("RETURN"), remap = false)
    private void probe$end(CallbackInfoReturnable<Object> ci) { Trace.event("client", Trace.client(), "iris_pipeline_return", false, null, null); }
}
