package net.muxigame.core.compat.mixin.pasterdream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Mirror the configured origin structure; adventure travel restrictions still apply separately. */
@Mixin(targets="com.pasterdream.pasterdreammod.world.PDOverworldOriginCrackWorldgen",remap=false)
public abstract class SurvivalOriginStructureMixin {
    @Redirect(method="onLevelLoad",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private static ResourceKey<Level> muxi$originEligibility(ServerLevel level) {
        return WorldDimensions.exploration(level.dimension())?Level.OVERWORLD:level.dimension();
    }
}
