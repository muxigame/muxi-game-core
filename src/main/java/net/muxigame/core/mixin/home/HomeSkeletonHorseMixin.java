package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
@Mixin(SkeletonHorse.class)
public abstract class HomeSkeletonHorseMixin {
 @Inject(method="aiStep",at=@At("HEAD")) private void muxi$disarm(CallbackInfo ci){var horse=(SkeletonHorse)(Object)this;if(HomeSpawningFeature.protectedHome(horse.level())&&horse.isTrap())horse.setTrap(false);}
}
