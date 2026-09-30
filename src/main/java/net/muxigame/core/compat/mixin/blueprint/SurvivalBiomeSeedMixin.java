package net.muxigame.core.compat.mixin.blueprint;

import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Blueprint adds a dimension salt; use the home salt and slice size for matching terrain. */
@Mixin(targets="com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSlicesManager",remap=false)
public abstract class SurvivalBiomeSeedMixin {
    @Redirect(method="onServerAboutToStart",at=@At(value="INVOKE",target="Lnet/minecraft/resources/ResourceLocation;hashCode()I"))
    private static int muxi$worldgenSalt(ResourceLocation location) {
        return (location.equals(WorldDimensions.OVERWORLD.location())?Level.OVERWORLD.location():location).hashCode();
    }
    @ModifyArg(method="onServerAboutToStart",at=@At(value="INVOKE",target="Lnet/minecraft/core/Registry;getData(Lnet/neoforged/neoforge/registries/datamaps/DataMapType;Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;"),index=1)
    private static ResourceKey<?> muxi$sliceSize(ResourceKey<?> key) {
        return key.location().equals(WorldDimensions.OVERWORLD.location())?ResourceKey.create(Registries.LEVEL_STEM,Level.OVERWORLD.location()):key;
    }
}
