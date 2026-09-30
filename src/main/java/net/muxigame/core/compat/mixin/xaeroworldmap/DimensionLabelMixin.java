package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the visible dropdown labels change; Xaero's IDs, map folders and waypoint data stay intact. */
@Mixin(targets = "xaero.map.gui.GuiMapSwitching", remap = false)
public abstract class DimensionLabelMixin {
    @Redirect(method = "createDimensionDropdown", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/resources/ResourceLocation;toString()Ljava/lang/String;"))
    private String muxi$dimensionLabel(ResourceLocation location) {
        return WorldDimensions.name(ResourceKey.create(Registries.DIMENSION, location));
    }
}
