package net.muxigame.core.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Public getChunkFuture managed-blocks on the main thread; invoke only its scheduling half. */
@Mixin(ServerChunkCache.class)
public interface InitialSpawnChunkInvoker {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> muxi$scheduleInitialSpawnChunk(int x, int z, ChunkStatus status, boolean create);
}
