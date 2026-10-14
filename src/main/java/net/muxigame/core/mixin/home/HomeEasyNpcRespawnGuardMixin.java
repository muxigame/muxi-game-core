package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Veto ambient replacement BEFORE the mod discards the original NPC. Explicit GUI/spawner paths retain their permissions. */
@Pseudo @Mixin(targets="de.markusbordihn.easynpc.handler.RespawnHandler")
public abstract class HomeEasyNpcRespawnGuardMixin {
 @Inject(method="respawnNPC",at=@At("HEAD"),cancellable=true) private static void muxi$replacement(@Coerce Object npc,ServerLevel level,CallbackInfoReturnable<Boolean> ci){var origin=HomeSpawnContext.current();if(HomeSpawningFeature.protectedHome(level)&&origin!=HomeSpawnContext.Origin.PLAYER&&origin!=HomeSpawnContext.Origin.SPAWNER&&origin!=HomeSpawnContext.Origin.TRAVEL)ci.setReturnValue(false);}
}
