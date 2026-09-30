package net.muxigame.core.threading.mixin;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChunkMap.class)
public abstract class ChunkPipelineMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method="applyStep",at=@At("HEAD"))
    private void muxi$tracePipeline(GenerationChunkHolder holder, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, CallbackInfoReturnable<?> callback) {
        DimensionThreads.generated(level, step.targetStatus().toString());
    }
}
