package net.muxigame.core.compat.mixin.pingwheel;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 标记点旁边"是谁标的"那个名字。Ping Wheel 自己拿登录名套 PlayerTeam.formatNameForTeam，
 * 不在 Simple Nicknames 的格式化上下文里，所以 SN 那边换不到；这里在取名字的那一下换，队伍颜色前后缀照旧。
 * 头像由同一个 PlayerInfo 另取，不受影响。
 */
@Mixin(targets = "nx.pingwheel.common.render.PingLocationRenderer", remap = false)
public abstract class PingLabelMixin {
    @ModifyExpressionValue(
        method = "draw(Lnx/pingwheel/common/render/DrawContext;Lnx/pingwheel/common/core/PingView;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private static String muxi$pingNickname(String name) {
        return Nicknames.display(name);
    }
}
