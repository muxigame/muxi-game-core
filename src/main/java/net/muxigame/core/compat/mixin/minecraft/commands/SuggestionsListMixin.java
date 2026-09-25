package net.muxigame.core.compat.mixin.minecraft.commands;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.muxigame.core.compat.commands.SuggestionLabels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 补全弹框的每一行：候选是 UID（或在线玩家的 UUID）时画成"UID 昵称"。
 *
 * <p>只换 drawString 收到的字，候选本身不动：Tab/回车插进去的、服务端解析的、Chat Heads 找头像用的都还是 UID。
 * Chat Heads 改的是同一个调用的 x（第 3 个参数），互不影响。
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public abstract class SuggestionsListMixin {
    @ModifyArg(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"),
        index = 1)
    private String muxi$nicknameLabel(String text) {
        return SuggestionLabels.label(text);
    }
}
