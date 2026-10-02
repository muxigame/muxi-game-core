package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.client.gui.GuiGraphics;
import net.muxigame.core.client.waystones.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(value=GuiMap.class,remap=false)
public abstract class NetworkMapFrameMixin {
    @Inject(method="render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",at=@At("HEAD"),require=1)
    private void muxi$networkFrame(GuiGraphics graphics,int mouseX,int mouseY,float delta,CallbackInfo ci) {
        StoneLabelLayout.beginFrame();WaystoneMapClient.poll();
    }
}
