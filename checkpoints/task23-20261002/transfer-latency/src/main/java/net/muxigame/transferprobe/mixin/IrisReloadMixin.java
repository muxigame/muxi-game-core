package net.muxigame.transferprobe.mixin;

import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public class IrisReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"), remap = false)
    private static void probe$reloadBegin(CallbackInfo ci) { Trace.event("client", Trace.client(), "iris_reload_begin", false, null, null); }
    @Inject(method = "reload", at = @At("RETURN"), remap = false)
    private static void probe$reloadEnd(CallbackInfo ci) { Trace.event("client", Trace.client(), "iris_reload_return", false, null, null); }
}
