package net.muxigame.core.compat.mixin.sereneseasons;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Whitelist inheritance also follows later configuration reloads. */
@Mixin(targets="sereneseasons.config.SeasonsConfig",remap=false)
public abstract class SurvivalSeasonsMixin {
    @ModifyVariable(method="isDimensionWhitelisted",at=@At("HEAD"),argsOnly=true)
    private ResourceKey<Level> muxi$homeSeasonRules(ResourceKey<Level> key) {
        return WorldDimensions.exploration(key)?Level.OVERWORLD:key;
    }
}
