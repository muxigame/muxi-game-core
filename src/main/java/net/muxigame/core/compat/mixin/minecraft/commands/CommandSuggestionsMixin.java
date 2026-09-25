package net.muxigame.core.compat.mixin.minecraft.commands;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.commands.SharedSuggestionProvider;
import net.muxigame.core.compat.commands.NicknameSuggestions;
import net.muxigame.core.compat.commands.SuggestionLabels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;

/** 补全弹框（聊天框、命令方块共用）。NeoForge 没改这个类，目标是原版字节码。 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    /** 行里画的是"UID 昵称"（见 SuggestionsListMixin），弹框宽度得按画的字量，不然昵称会画到框外。 */
    @WrapOperation(
        method = "showSuggestions(Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
    private int muxi$labelWidth(Font font, String text, Operation<Integer> original) {
        return original.call(font, SuggestionLabels.label(text));
    }

    /** 标记"正在给指令算候选"：命令方块里的指令不带 '/'，单看输入分不出它和普通聊天。 */
    @WrapOperation(
        method = "updateCommandInfo()V",
        at = @At(value = "INVOKE",
                 target = "Lcom/mojang/brigadier/CommandDispatcher;getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Suggestions> muxi$markCommandInput(CommandDispatcher<SharedSuggestionProvider> dispatcher,
                                                               ParseResults<SharedSuggestionProvider> parse, int cursor,
                                                               Operation<CompletableFuture<Suggestions>> original) {
        boolean previous = NicknameSuggestions.enterCommandInput();
        try {
            return original.call(dispatcher, parse, cursor);
        } finally {
            NicknameSuggestions.leaveCommandInput(previous);
        }
    }
}
