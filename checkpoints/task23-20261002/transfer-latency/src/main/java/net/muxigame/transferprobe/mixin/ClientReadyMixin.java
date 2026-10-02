package net.muxigame.transferprobe.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.LevelLoadStatusManager;
import net.muxigame.transferprobe.Trace;
import net.muxigame.transferprobe.ProbeWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadStatusManager.class)
public abstract class ClientReadyMixin {
    @Shadow public abstract boolean levelReady();
    @Unique private long probe$nextSample;
    @Unique private boolean probe$readyRecorded;
    @Inject(method = "tick", at = @At("RETURN"))
    private void probe$ready(CallbackInfo ci) {
        if (!Trace.ENABLED) return;
        ProbeWindow w = Trace.client();
        if (w == null || probe$readyRecorded || System.nanoTime() - w.started >= ProbeWindow.LIMIT_NANOS) return;
        if (levelReady()) {
            probe$readyRecorded = true;
            Minecraft mc = Minecraft.getInstance();
            Trace.client("level_ready");
            if (mc.player != null && mc.level != null && mc.player.isAlive() && !mc.player.isSpectator()
                    && !mc.level.isOutsideBuildHeight(mc.player.getBlockY())
                    && mc.levelRenderer.isSectionCompiled(mc.player.blockPosition())) Trace.client("section_ready");
            return;
        }
        long now = System.nanoTime();
        if (now < probe$nextSample) return;
        probe$nextSample = now + 1_000_000_000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            boolean hasChunk = mc.level.hasChunkAt(mc.player.blockPosition());
            Trace.event("client", Trace.client(), "ready_wait", false, "has_landing_chunk", hasChunk);
        }
    }
}
