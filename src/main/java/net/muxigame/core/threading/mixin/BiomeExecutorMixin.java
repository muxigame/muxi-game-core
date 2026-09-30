package net.muxigame.core.threading.mixin;
import net.minecraft.Util;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.concurrent.ExecutorService;
@Mixin(ChunkGenerator.class)
public abstract class BiomeExecutorMixin {
    @Redirect(method="createBiomes",at=@At(value="INVOKE",target="Lnet/minecraft/Util;backgroundExecutor()Ljava/util/concurrent/ExecutorService;"))
    private ExecutorService muxi$biomeExecutor() { return DimensionThreads.generationExecutor(Util.backgroundExecutor()); }
}
