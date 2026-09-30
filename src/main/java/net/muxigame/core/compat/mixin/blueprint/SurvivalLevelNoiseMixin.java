package net.muxigame.core.compat.mixin.blueprint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Match Blueprint's per-level feature noise without changing the world's identity. */
@Mixin(targets="com.teamabnormals.blueprint.common.world.storage.receiver.LevelNoiseReceiver",remap=false)
public abstract class SurvivalLevelNoiseMixin {
    @Redirect(method="create(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/levelgen/synth/NormalNoise;",
        at=@At(value="INVOKE",target="Lnet/minecraft/resources/ResourceLocation;hashCode()I"))
    private int muxi$featureNoiseSalt(ResourceLocation location) {
        return (location.equals(WorldDimensions.OVERWORLD.location())?Level.OVERWORLD.location():location).hashCode();
    }
}
