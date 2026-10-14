package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
@Mixin(net.minecraft.world.entity.npc.Villager.class)
public abstract class HomeVillageGolemMixin {
 @Inject(method="spawnGolemIfNeeded",at=@At("HEAD"),cancellable=true) private void muxi$ambient(ServerLevel level,long tick,int minimum,CallbackInfo ci){if(HomeSpawningFeature.protectedHome(level))ci.cancel();}
}
