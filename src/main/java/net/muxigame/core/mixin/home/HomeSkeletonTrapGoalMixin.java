package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.entity.animal.horse.*;
@Mixin(SkeletonTrapGoal.class)
public abstract class HomeSkeletonTrapGoalMixin {
 @Shadow @Final private SkeletonHorse horse;
 @Inject(method="canUse",at=@At("HEAD"),cancellable=true) private void muxi$noTrigger(CallbackInfoReturnable<Boolean> ci){if(HomeSpawningFeature.protectedHome(horse.level()))ci.setReturnValue(false);}
 @Inject(method={"tick","lambda$tick$0"},at=@At("HEAD"),cancellable=true) private void muxi$noRiders(CallbackInfo ci){if(HomeSpawningFeature.protectedHome(horse.level()))ci.cancel();}
}
