package net.muxigame.core.compat.mixin.xaerominimap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 别人分享路径点时聊天栏里"某某 分享了路径点 X"那句话里的某某。
 *
 * <p>这个名字一路只传到 WaypointSharingHandler.onWaypointReceived，在那里只当翻译参数显示；
 * 路径点的解析、"添加"按钮的点击指令都不用它，所以在发送者的 GameProfile 名刚取出来时换即可。
 * 只换玩家聊天这条路：这里拿到的一定是登录名。系统消息那条路抠的是服务端印出来的尖括号里的字，
 * 可能已经是昵称，不拿它去反查。
 */
@Mixin(targets = "xaero.common.events.ClientEvents", remap = false)
public abstract class WaypointShareNoticeMixin {
    @ModifyExpressionValue(
        method = "handleClientPlayerChatReceivedEvent(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;)Z",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private String muxi$senderNickname(String login) {
        return Nicknames.display(login);
    }
}
