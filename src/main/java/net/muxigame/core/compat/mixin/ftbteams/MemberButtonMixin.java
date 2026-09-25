package net.muxigame.core.compat.mixin.ftbteams;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * "我的队伍"成员按钮：按钮上的名字，和右键菜单里"提升/降级/转让/踢出/移除盟友 %s"及其确认文字。
 * 菜单项真正执行时发的是整个 KnownClientPlayer（服务端按 UUID 处理），名字只进了这些翻译参数。
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.MemberButton", remap = false)
public abstract class MemberButtonMixin {
    // 按钮标题在调用父类构造之前拼，只能用 static 处理方法；这里只有这一处 Component.literal，就是名字。
    @ModifyArg(
        method = "<init>(Ldev/ftb/mods/ftblibrary/ui/Panel;Ldev/ftb/mods/ftbteams/api/client/KnownClientPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
        index = 0)
    private static String muxi$buttonNickname(String name) {
        return Nicknames.display(name);
    }

    // onClicked 里 10 处 name() 全部直接进了 Component.translatable 的参数。
    @ModifyExpressionValue(
        method = "onClicked(Ldev/ftb/mods/ftblibrary/ui/input/MouseButton;)V",
        at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/client/KnownClientPlayer;name()Ljava/lang/String;"))
    private static String muxi$menuNickname(String name) {
        return Nicknames.display(name);
    }
}
