package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.muxigame.core.client.waystones.WaystoneMapClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;
import xaero.map.mods.gui.WaypointReader;
import java.util.ArrayList;

/** Replaces Xaero's generic /tp waypoint action with a server-validated Waystones warp. */
@Mixin(value = WaypointReader.class, remap = false)
public abstract class WaystoneWaypointMenuMixin {
    @Inject(
        method = "getRightClickOptions(Lxaero/map/mods/gui/Waypoint;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;",
        at = @At("RETURN"))
    private void muxi$waystoneTeleportOption(Waypoint waypoint, IRightClickableElement target,
            CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        if (!WaystoneMapClient.isWaystoneOrigin(waypoint.getThirdPartyOrigin())) return;
        ArrayList<RightClickOption> options = cir.getReturnValue();
        if (options == null || options.size() <= 3) return;
        boolean available = WaystoneMapClient.nearSource();
        String key = available ? "muxi.map.waystone.teleport" : "muxi.map.waystone.teleport_requires_source";
        RightClickOption option = new RightClickOption(key, 3, target) {
            @Override public void onAction(Screen screen) {
                ResourceKey<Level> dimension = null;
                if (screen instanceof GuiMapAccessor accessor) dimension = accessor.muxi$mouseBlockDim();
                Minecraft mc = Minecraft.getInstance();
                if (dimension == null && mc.level != null) dimension = mc.level.dimension();
                if (dimension != null) {
                    WaystoneMapClient.teleport(dimension, new BlockPos(waypoint.getX(), waypoint.getY(), waypoint.getZ()));
                }
            }
        }.setActive(available);
        options.set(3, option);
    }
}
