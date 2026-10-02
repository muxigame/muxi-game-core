package net.muxigame.transferprobe.mixin;

import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder", remap = false)
public class SodiumWorkersMixin {
    @Inject(method = "shutdownThreads", at = @At("HEAD"), remap = false)
    private void probe$begin(CallbackInfo ci) { Trace.event("client", Trace.client(), "sodium_shutdown_begin", false, null, null); }
    @Inject(method = "shutdownThreads", at = @At("RETURN"), remap = false)
    private void probe$end(CallbackInfo ci) { Trace.event("client", Trace.client(), "sodium_shutdown_return", false, null, null); }
}
