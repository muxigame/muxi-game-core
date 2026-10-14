package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
@Mixin(Animal.class)
public abstract class HomeBreedingContextMixin {
 @WrapMethod(method="spawnChildFromBreeding") private void muxi$breed(ServerLevel level,Animal mate,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(level,mate);}}
}
