package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.muxigame.core.client.waystones.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import xaero.map.mods.gui.*;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

@Mixin(value=WaypointRenderer.class,remap=false)
public abstract class NetworkStoneRendererMixin {
    @Inject(method="renderElement(Lxaero/map/mods/gui/Waypoint;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",at=@At("HEAD"),cancellable=true,require=1)
    private void muxi$stone(Waypoint waypoint,boolean hovered,double optionalScale,float scale,double x,double y,
        ElementRenderInfo info,GuiGraphics graphics,MultiBufferSource.BufferSource buffers,MultiTextureRenderTypeRendererProvider textures,CallbackInfoReturnable<Boolean> cir) {
        if(!WaystoneMapClient.isWaystoneOrigin(waypoint.getThirdPartyOrigin()))return;
        NativeStoneRenderer.draw(waypoint,hovered,scale*((WaypointRenderer)(Object)this).getContext().worldmapWaypointsScale,x,y,graphics);
        cir.setReturnValue(true);
    }
    @Inject(method="renderElementShadow(Lxaero/map/mods/gui/Waypoint;ZFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)V",at=@At("HEAD"),cancellable=true,require=1)
    private void muxi$stoneShadow(Waypoint waypoint,boolean hovered,float scale,double x,double y,
        ElementRenderInfo info,GuiGraphics graphics,MultiBufferSource.BufferSource buffers,MultiTextureRenderTypeRendererProvider textures,CallbackInfo ci) {
        if(WaystoneMapClient.isWaystoneOrigin(waypoint.getThirdPartyOrigin()))ci.cancel();
    }
}
