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
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private java.util.Map<?,?> pipelinesPerDimension;
    @org.spongepowered.asm.mixin.Unique private boolean muxi$building;
    @Inject(method = "preparePipeline", at = @At("HEAD"), remap = false)
    private void probe$begin(@org.spongepowered.asm.mixin.injection.Coerce Object dimension, CallbackInfoReturnable<Object> ci) { muxi$building=!pipelinesPerDimension.containsKey(dimension); if(muxi$building) Trace.event("client", Trace.client(), "iris_pipeline_begin", false, null, null); }
    @Inject(method = "preparePipeline", at = @At("RETURN"), remap = false)
    private void probe$end(@org.spongepowered.asm.mixin.injection.Coerce Object dimension, CallbackInfoReturnable<Object> ci) { if(muxi$building) Trace.event("client", Trace.client(), "iris_pipeline_return", false, null, null); }
}
