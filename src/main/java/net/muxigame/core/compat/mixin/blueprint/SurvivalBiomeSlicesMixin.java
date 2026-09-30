package net.muxigame.core.compat.mixin.blueprint;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.HashSet;

/** Inherit only overworld biome slices; never change a world's registered identity. */
@Mixin(targets="com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSlice",remap=false)
public abstract class SurvivalBiomeSlicesMixin {
    @Inject(method="levels",at=@At("RETURN"),cancellable=true)
    private void muxi$survivalSlices(CallbackInfoReturnable<HashSet<ResourceKey<Level>>> result) {
        var levels=result.getReturnValue();
        if(levels.contains(Level.OVERWORLD)&&!levels.contains(WorldDimensions.OVERWORLD)) {
            var extended=new HashSet<>(levels);extended.add(WorldDimensions.OVERWORLD);result.setReturnValue(extended);
        }
    }
}
