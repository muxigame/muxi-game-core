package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
@Pseudo @Mixin(targets={"net.minecraft.world.level.BaseSpawner","de.markusbordihn.easynpc.level.BaseEasyNPCSpawner"})
public abstract class HomeSpawnerContextMixin {
 @WrapMethod(method="serverTick") private void muxi$spawner(ServerLevel level,BlockPos pos,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.SPAWNER)){original.call(level,pos);}}
}
