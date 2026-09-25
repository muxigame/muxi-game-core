package net.muxigame.core.compat.mixin.ftbteams;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 邀请/结盟/建队时勾选玩家的那一行"☐ 名字"。构造时和每次勾选时各拼一次标题，两处都换。
 * 勾选状态和最后发给服务端的都是 KnownClientPlayer.profile()，不经过这个名字。
 * 构造里那一处在调用父类构造之前，处理方法必须是 static。
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.InvitedButton", remap = false)
public abstract class InvitedButtonMixin {
    @ModifyExpressionValue(
        method = {
            "<init>(Ldev/ftb/mods/ftblibrary/ui/Panel;Ldev/ftb/mods/ftbteams/client/gui/InvitationSetup;Ldev/ftb/mods/ftbteams/api/client/KnownClientPlayer;)V",
            "onClicked(Ldev/ftb/mods/ftblibrary/ui/input/MouseButton;)V"
        },
        at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/client/KnownClientPlayer;name()Ljava/lang/String;"))
    private static String muxi$rowNickname(String name) {
        return Nicknames.display(name);
    }
}
