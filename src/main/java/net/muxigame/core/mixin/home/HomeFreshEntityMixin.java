package net.muxigame.core.mixin.home;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.muxigame.core.feature.spawning.HomeSpawningFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Fresh insertion is vetoed before another event subscriber can uncancel it. Disk loading uses another path. */
@Mixin(value=ServerLevel.class,priority=2000)
public abstract class HomeFreshEntityMixin {
 @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true) private void muxi$fresh(Entity entity,CallbackInfoReturnable<Boolean> ci){if(HomeSpawningFeature.denyFresh((ServerLevel)(Object)this,entity))ci.setReturnValue(false);}
}
