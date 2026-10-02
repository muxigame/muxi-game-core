package net.muxigame.transferprobe.mixin;

import net.muxigame.transferprobe.ProbeWindow;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager", remap = false)
public class EuphoriaReloadMixin {
    @Inject(method = "findAndScheduleReload", at = @At("HEAD"), cancellable = true, remap = false)
    private static void probe$schedule(CallbackInfo ci) {
        boolean dimensionCaller = Trace.dimensionReloadCaller();
        ProbeWindow w = Trace.client();
        if (dimensionCaller) Trace.event("client", w, "euphoria_dimension_reload_request", false, null, null);
        if (w != null && System.nanoTime() - w.started < ProbeWindow.LIMIT_NANOS
                && ProbeWindow.maySkipExtraReload(Trace.SKIP_EXTRA_RELOAD, dimensionCaller, w.source, w.target)) {
            Trace.event("client", w, "euphoria_extra_reload_skipped", false, null, null);
            ci.cancel();
        }
    }
}
