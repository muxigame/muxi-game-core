package net.muxigame.core.compat.mixin.xaerominimap;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.hud.compat.mods.SupportWaystones;

/**
 * Xaero also exposes some unactivated GLOBAL waystones as "other" waypoints.
 * Our pack rule is stricter: normal waystones are map-visible only after activation;
 * sharestones are the sole activation-free exception.
 */
@Mixin(value = SupportWaystones.class, remap = false)
public abstract class WaystoneMarkerFilterMixin {
    @Inject(method = "addWaypoint", at = @At("HEAD"), cancellable = true)
    private void muxi$onlyActivatedOrSharestone(Waystone waystone, MinimapWorldRootContainer root, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || WaystoneTypes.isSharestone(waystone.getWaystoneType())) return;
        if (!WaystonesAPI.isWaystoneActivated(player, waystone)) ci.cancel();
    }
}
