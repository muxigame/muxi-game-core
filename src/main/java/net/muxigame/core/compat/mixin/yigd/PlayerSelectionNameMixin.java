package net.muxigame.core.compat.mixin.yigd;

import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 管理员"按玩家看坟墓"列表里每行画出来的名字。
 * 只换画字这一下：点进去发给服务端的仍是原来那份 ResolvableProfile，搜索框仍按登录名过滤。
 */
@Mixin(targets = "com.b1n_ry.yigd.client.gui.PlayerSelectionScreen", remap = false)
public abstract class PlayerSelectionNameMixin {
    @ModifyArg(
        method = "renderScrollbar(Lnet/minecraft/client/gui/GuiGraphics;IIFII)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
        index = 1)
    private static String muxi$rowNickname(String name) {
        return Nicknames.display(name);
    }
}
