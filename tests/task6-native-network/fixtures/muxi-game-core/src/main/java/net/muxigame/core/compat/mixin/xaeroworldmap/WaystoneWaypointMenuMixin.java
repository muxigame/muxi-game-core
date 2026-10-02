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
        at = @At("RETURN"), require = 1)
    private void muxi$waystoneTeleportOption(Waypoint waypoint, IRightClickableElement target,
            CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        if (!WaystoneMapClient.isWaystoneOrigin(waypoint.getThirdPartyOrigin())) return;
        ArrayList<RightClickOption> options = cir.getReturnValue();
        if (options == null || options.size() <= 3) return;
        ResourceKey<Level> selected = net.muxigame.core.client.waystones.NativeStoneRenderer.viewDimension();
        BlockPos position = new BlockPos(waypoint.getX(),waypoint.getY(),waypoint.getZ());
        String unavailable = WaystoneMapClient.unavailableKey(selected,position);
        boolean available = unavailable==null;
        String key = available ? "muxi.map.waystone.teleport" : unavailable;
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
        if(WaystoneMapClient.portalLinked(selected,position)) {
            options.add(new RightClickOption("muxi.map.waystone.portal_connected",options.size(),target) {
                @Override public void onAction(Screen screen) {}
            }.setActive(false));
        }
    }
    @Inject(method="getMenuName(Lxaero/map/mods/gui/Waypoint;)Ljava/lang/String;",at=@At("RETURN"),cancellable=true,require=1)
    private void muxi$connectedSelection(Waypoint waypoint,CallbackInfoReturnable<String> cir) {
        if(WaystoneMapClient.isWaystoneOrigin(waypoint.getThirdPartyOrigin()) && WaystoneMapClient.portalLinked(
            net.muxigame.core.client.waystones.NativeStoneRenderer.viewDimension(),new BlockPos(waypoint.getX(),waypoint.getY(),waypoint.getZ()))) {
            cir.setReturnValue(cir.getReturnValue()+" · "+net.minecraft.network.chat.Component.translatable("muxi.map.waystone.portal_connected").getString());
        }
    }
}
