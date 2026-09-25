package net.muxigame.core.compat.mixin.xaeroworldmap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 世界地图上被追踪玩家头像旁、鼠标悬停时淡入的名字。方法里唯一一次 GameProfile.getName 只用来量宽和画字。
 */
@Mixin(targets = "xaero.map.radar.tracker.PlayerTrackerMapElementRenderer", remap = false)
public abstract class TrackedPlayerMarkerMixin {
    @ModifyExpressionValue(
        method = "renderElement(Lxaero/map/radar/tracker/PlayerTrackerMapElement;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private String muxi$markerNickname(String login) {
        return Nicknames.display(login);
    }
}
