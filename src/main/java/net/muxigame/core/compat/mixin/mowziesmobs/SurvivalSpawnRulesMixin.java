package net.muxigame.core.compat.mixin.mowziesmobs;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Inherit overworld whitelist eligibility inside this predicate only; all other spawn checks remain. */
@Mixin(targets="com.bobmowzie.mowziesmobs.server.entity.MowzieEntity",remap=false)
public abstract class SurvivalSpawnRulesMixin {
    @Redirect(method="spawnPredicate",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private static ResourceKey<Level> muxi$allowedSurvival(ServerLevel level) {
        return WorldDimensions.exploration(level.dimension())?Level.OVERWORLD:level.dimension();
    }
}
