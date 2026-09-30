package net.muxigame.core.compat.mixin.twilightforest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="twilightforest.events.ProgressionEvents",remap=false)
public abstract class TwilightPortalCreationMixin {
    @Inject(method="checkForPortalCreation",at=@At("HEAD"),cancellable=true)
    private static void muxi$restrictAdventurePortals(ServerPlayer player,Level level,float range,CallbackInfo callback) {
        if(WorldDimensions.exploration(level.dimension()))callback.cancel();
    }
}
