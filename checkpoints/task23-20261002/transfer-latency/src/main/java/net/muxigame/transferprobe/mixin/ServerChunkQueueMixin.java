package net.muxigame.transferprobe.mixin;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.muxigame.transferprobe.ProbeWindow;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public class ServerChunkQueueMixin {
    @Shadow private LongSet pendingChunks;
    @Shadow private float desiredChunksPerTick;
    @Shadow private float batchQuota;
    @Shadow private int unacknowledgedBatches;
    @Shadow private int maxUnacknowledgedBatches;
    @Unique private long probe$nextSample;
    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void probe$sample(ServerPlayer player, CallbackInfo ci) {
        if (!Trace.ENABLED) return;
        ProbeWindow w = Trace.server(player.connection);
        long now = System.nanoTime();
        if (w == null || now - w.started >= ProbeWindow.LIMIT_NANOS || now < probe$nextSample) return;
        probe$nextSample = now + 1_000_000_000L;
        JsonObject queue = new JsonObject();
        queue.addProperty("pending", pendingChunks.size());
        queue.addProperty("desired_per_tick", desiredChunksPerTick);
        queue.addProperty("batch_quota", batchQuota);
        queue.addProperty("unacknowledged", unacknowledgedBatches);
        queue.addProperty("max_unacknowledged", maxUnacknowledgedBatches);
        if (w.positionKnown) queue.addProperty("landing_pending", pendingChunks.contains(ChunkPos.asLong(w.chunkX, w.chunkZ)));
        Trace.event("server", w, "chunk_queue_sample", false, "queue", queue);
    }
}
