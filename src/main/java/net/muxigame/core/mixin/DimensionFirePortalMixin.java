package net.muxigame.core.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adventure portals are restricted to home; custom inter-world gates use their own ignition. */
@Mixin(value=BaseFireBlock.class,remap=false)
public abstract class DimensionFirePortalMixin {
    @Inject(method="inPortalDimension",at=@At("HEAD"),cancellable=true)
    private static void muxi$restrictAdventurePortals(Level level,CallbackInfoReturnable<Boolean> result) {
        if(WorldDimensions.exploration(level.dimension()))result.setReturnValue(false);
    }
}
