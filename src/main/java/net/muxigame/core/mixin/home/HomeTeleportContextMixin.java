package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
@Mixin(ServerLevel.class)
public abstract class HomeTeleportContextMixin {
 @WrapMethod(method="addDuringTeleport") private void muxi$travel(Entity entity,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.TRAVEL)){original.call(entity);}}
}
