package net.muxigame.core.compat.mixin.xaerominimap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 被追踪玩家（队友，超出视距也能看到）头像上方的名字，画在世界里（IN_WORLD）。
 *
 * <p>方法里唯一一次 GameProfile.getName 只用来量宽度和画字，换成昵称后底框跟着按昵称宽度画。
 */
@Mixin(targets = "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer", remap = false)
public abstract class TrackedPlayerLabelMixin {
    @ModifyExpressionValue(
        method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private String muxi$trackedNickname(String login) {
        return Nicknames.display(login);
    }
}
