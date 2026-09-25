package net.muxigame.core.compat.mixin.xaeroworldmap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.element.render.ElementReader;
import xaero.map.radar.tracker.PlayerTrackerMapElementReader;

/**
 * 世界地图侧栏列表每一项的文字。这个渲染器路径点、玩家共用，只换玩家那一种——
 * 叫"10000"的路径点不能被当成玩家名换掉。
 */
@Mixin(targets = "xaero.map.element.MapElementMenuRenderer", remap = false)
public abstract class MenuEntryNameMixin {
    @WrapOperation(
        method = "renderMenuElement(Lxaero/map/element/render/ElementReader;Ljava/lang/Object;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/screens/Screen;IIIIDZZLnet/minecraft/client/Minecraft;Z)V",
        at = @At(value = "INVOKE", target = "Lxaero/map/element/render/ElementReader;getMenuName(Ljava/lang/Object;)Ljava/lang/String;"))
    private String muxi$playerEntryNickname(ElementReader<?, ?, ?> reader, Object element, Operation<String> original) {
        String name = original.call(reader, element);
        return reader instanceof PlayerTrackerMapElementReader ? Nicknames.display(name) : name;
    }
}
