package net.muxigame.core.threading.mixin;
import net.minecraft.server.level.ServerLevel;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.concurrent.Executor;
@Mixin(ServerLevel.class)
public abstract class LevelLoaderMixin {
    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache;<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/chunk/ChunkGenerator;IIZLnet/minecraft/server/level/progress/ChunkProgressListener;Lnet/minecraft/world/level/entity/ChunkStatusUpdateListener;Ljava/util/function/Supplier;)V"),index=4)
    private Executor muxi$worldLoader(Executor original) { return DimensionThreads.loader((ServerLevel)(Object)this, original); }
}
