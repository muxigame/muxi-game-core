package net.muxigame.core.threading.mixin;
import net.minecraft.Util;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.concurrent.ExecutorService;
/** Only nonblocking native generation continuations; IOWorker must retain its auxiliary pool. */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseExecutorMixin {
    @Redirect(method={"createBiomes","fillFromNoise"},at=@At(value="INVOKE",target="Lnet/minecraft/Util;backgroundExecutor()Ljava/util/concurrent/ExecutorService;"))
    private ExecutorService muxi$generationExecutor() { return DimensionThreads.generationExecutor(Util.backgroundExecutor()); }
}
