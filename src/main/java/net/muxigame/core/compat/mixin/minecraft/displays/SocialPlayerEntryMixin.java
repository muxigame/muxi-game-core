package net.muxigame.core.compat.mixin.minecraft.displays;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.muxigame.core.compat.displays.DisplayNames;
import net.muxigame.core.nickname.Nicknames;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 社交界面（P 键，暂停菜单的"玩家举报"也是它）的玩家行。
 *
 * <p>playerName 字段不能改：getPlayerName() 给搜索过滤、排序和举报（NameReportScreen 报的就是这个名字）用，
 * 屏蔽/显示按 UUID。所以只在读字段画字、拼提示和旁白的几处换，外加构造里拼的屏蔽/显示旁白和点按钮后发到聊天栏的提示。
 */
@Mixin(value = PlayerEntry.class, remap = false)
public abstract class SocialPlayerEntryMixin {
    // render 画名字、举报按钮提示、整行旁白：这几个方法里读 playerName 全是给人看的。
    @ModifyExpressionValue(
        method = {
            "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V",
            "createReportButtonTooltip()Lnet/minecraft/client/gui/components/Tooltip;",
            "getEntryNarationMessage(Lnet/minecraft/network/chat/MutableComponent;)Lnet/minecraft/network/chat/MutableComponent;"
        },
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                 target = "Lnet/minecraft/client/gui/screens/social/PlayerEntry;playerName:Ljava/lang/String;"))
    private static String muxi$shownName(String name) {
        return Nicknames.display(name);
    }

    // 构造里带参数的 translatable 只有屏蔽/显示两句旁白；两个 lambda 是点屏蔽/显示后发到聊天栏的那句话。
    // lambda 先按 UUID 调 hidePlayer/showPlayer，名字只进这句提示。
    @ModifyArg(
        method = {
            "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/social/SocialInteractionsScreen;Ljava/util/UUID;Ljava/lang/String;Ljava/util/function/Supplier;Z)V",
            "lambda$new$2(Lnet/minecraft/client/gui/screens/social/PlayerSocialManager;Ljava/util/UUID;Ljava/lang/String;Lnet/minecraft/client/gui/components/Button;)V",
            "lambda$new$3(Lnet/minecraft/client/gui/screens/social/PlayerSocialManager;Ljava/util/UUID;Ljava/lang/String;Lnet/minecraft/client/gui/components/Button;)V"
        },
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
        index = 1)
    private static Object[] muxi$messageArgs(Object[] args) {
        return DisplayNames.args(args);
    }
}
