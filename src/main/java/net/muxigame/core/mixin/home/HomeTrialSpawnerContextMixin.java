package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
@Mixin(net.minecraft.world.level.block.entity.trialspawner.TrialSpawner.class)
public abstract class HomeTrialSpawnerContextMixin {
 @WrapMethod(method="tickServer") private void muxi$spawner(ServerLevel level,BlockPos pos,boolean ominous,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.SPAWNER)){original.call(level,pos,ominous);}}
}
