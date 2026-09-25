package net.muxigame.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.muxigame.core.feature.spawning.SpawnCategoryFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 刷怪循环里每个类别先问"这个维度能不能刷出东西"，不能就当作已满上限跳过（见 SpawnCategoryFilter）。
 * require = 0：别的模组改了这处调用时只是没有这个优化，不会启动崩溃。
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @WrapOperation(
        method = "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;canSpawnForCategory(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z"),
        require = 0)
    private static boolean muxi$skipImpossibleCategory(NaturalSpawner.SpawnState state, MobCategory category, ChunkPos chunk,
                                                       Operation<Boolean> original, @Local(argsOnly = true) ServerLevel level) {
        return !SpawnCategoryFilter.impossible(level, category) && original.call(state, category, chunk);
    }
}
