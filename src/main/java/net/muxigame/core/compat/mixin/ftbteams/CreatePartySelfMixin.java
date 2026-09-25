package net.muxigame.core.compat.mixin.ftbteams;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 建队界面成员栏里代表自己的那一行（队长）。只是一个显示用的按钮，头像另取 GameProfile。
 * 同一界面"队伍名"输入框的默认值（"%s's Party"）也用登录名，但那会被存成队名，属于持久化数据，不在这里换。
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.CreatePartyScreen$InvitePanel", remap = false)
public abstract class CreatePartySelfMixin {
    @ModifyExpressionValue(
        method = "addWidgets()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/User;getName()Ljava/lang/String;"))
    private static String muxi$selfNickname(String name) {
        return Nicknames.display(name);
    }
}
