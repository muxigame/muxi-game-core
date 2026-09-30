package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.resources.ResourceLocation;
import net.muxigame.core.client.waystones.WaystoneMapClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.map.mods.gui.Waypoint;

/** Gray Waystones map markers until the player is standing beside a source waystone. */
@Mixin(value = Waypoint.class, remap = false)
public abstract class WaystoneWaypointColorMixin {
    @Shadow public abstract ResourceLocation getThirdPartyOrigin();

    @ModifyReturnValue(method = "getColor", at = @At("RETURN"))
    private int muxi$grayUnavailableWaystone(int original) {
        return WaystoneMapClient.isWaystoneOrigin(getThirdPartyOrigin()) && !WaystoneMapClient.nearSource()
            ? WaypointColor.GRAY.getHex() : original;
    }
}
