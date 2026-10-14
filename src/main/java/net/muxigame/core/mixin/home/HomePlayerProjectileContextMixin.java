package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
@Mixin(ServerLevel.class)
public abstract class HomePlayerProjectileContextMixin {
 @WrapMethod(method="tickNonPassenger") private void muxi$projectile(Entity entity,Operation<Void> original){boolean deliberate=(entity instanceof Projectile p&&p.getOwner() instanceof ServerPlayer)||(entity instanceof PlayerOwnedHatch egg&&egg.muxi$hatchOwner()!=null);if(deliberate){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(entity);}}else original.call(entity);}
}
