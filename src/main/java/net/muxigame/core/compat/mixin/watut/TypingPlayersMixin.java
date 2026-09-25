package net.muxigame.core.compat.mixin.watut;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 聊天框上方"X 正在输入…"。getTypingPlayers 只拼这一行给 onGuiRender 画，名字不作别用。
 * 超过字数上限改显示"多人正在输入"的判断也跟着按昵称长度算，正是想要的。
 */
@Mixin(targets = "com.corosus.watut.PlayerStatusManagerClient", remap = false)
public abstract class TypingPlayersMixin {
    @ModifyExpressionValue(
        method = "getTypingPlayers()Ljava/lang/String;",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private static String muxi$typingNickname(String name) {
        return Nicknames.display(name);
    }
}
