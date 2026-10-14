package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.*;
@Mixin(value=WorldGenRegion.class,priority=2000)
public abstract class HomeWorldGenMixin {
 @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true) private void muxi$worldgen(Entity entity,CallbackInfoReturnable<Boolean> ci){if(entity instanceof Mob&&HomeSpawningFeature.protectedHome(((WorldGenRegion)(Object)this).getLevel()))ci.setReturnValue(false);}
}
