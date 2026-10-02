package net.muxigame.transferprobe.mixin;

import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public class SodiumResetMixin {
    @Inject(method = "initRenderer", at = @At("HEAD"), remap = false)
    private void probe$begin(CallbackInfo ci) { Trace.event("client", Trace.client(), "sodium_reset_begin", false, null, null); }
    @Inject(method = "initRenderer", at = @At("RETURN"), remap = false)
    private void probe$end(CallbackInfo ci) { Trace.event("client", Trace.client(), "sodium_reset_return", false, null, null); }
}
