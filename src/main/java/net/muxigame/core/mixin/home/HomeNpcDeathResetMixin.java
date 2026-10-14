package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import net.minecraft.world.entity.Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
/** Prevent periodic resurrection, not normal entity loading, manual reset or normal death handling. */
@Pseudo @Mixin(targets="noppes.npcs.entity.EntityNPCInterface")
public abstract class HomeNpcDeathResetMixin {
 @WrapOperation(method="tickDeath",at=@At(value="INVOKE",target="Lnoppes/npcs/entity/EntityNPCInterface;reset()V")) private void muxi$ambientReset(@Coerce Object npc,Operation<Void> original){var origin=HomeSpawnContext.current();if(!HomeSpawningFeature.protectedHome(((Entity)npc).level())||origin==HomeSpawnContext.Origin.PLAYER||origin==HomeSpawnContext.Origin.SPAWNER||origin==HomeSpawnContext.Origin.TRAVEL)original.call(npc);}
}
