package net.muxigame.core.compat.mixin.minecraft.commands;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.muxigame.core.compat.commands.NicknameSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 字符串候选的三个 suggest（玩家名、档案名、计分项持有者、模组自己列的玩家）：原版只按前缀筛，
 * 这里放宽成"前缀或昵称"。每个候选只判一次、仍由原版自己 suggest，所以不会重复，插进去的也还是原来的候选。
 * 两边都套：客户端算实体/档案参数，服务端算 ask_server 的参数；普通聊天的 Tab 由 NicknameSuggestions 排除。
 * 前两个拿到的候选是原版转过小写的：UID 是纯数字不受影响，别的名字查不到昵称，就等于不补。
 */
@Mixin(SharedSuggestionProvider.class)
public interface SharedSuggestionProviderMixin {
    @WrapOperation(
        method = "suggest(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/SharedSuggestionProvider;matchesSubStr(Ljava/lang/String;Ljava/lang/String;)Z"))
    private static boolean muxi$nicknameIterable(String remaining, String candidate, Operation<Boolean> original,
                                                 @Local(argsOnly = true) SuggestionsBuilder builder) {
        return original.call(remaining, candidate) || NicknameSuggestions.matches(builder, remaining, candidate);
    }

    @WrapOperation(
        method = "suggest([Ljava/lang/String;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/SharedSuggestionProvider;matchesSubStr(Ljava/lang/String;Ljava/lang/String;)Z"))
    private static boolean muxi$nicknameArray(String remaining, String candidate, Operation<Boolean> original,
                                              @Local(argsOnly = true) SuggestionsBuilder builder) {
        return original.call(remaining, candidate) || NicknameSuggestions.matches(builder, remaining, candidate);
    }

    /** Stream 版在 lambda 里比，拿不到 builder；在它调 filter 的地方换条件。 */
    @WrapOperation(
        method = "suggest(Ljava/util/stream/Stream;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private static Stream<String> muxi$nicknameStream(Stream<String> names, Predicate<? super String> filter,
                                                      Operation<Stream<String>> original,
                                                      @Local(argsOnly = true) SuggestionsBuilder builder) {
        return original.call(names, NicknameSuggestions.widen(filter, builder));
    }
}
