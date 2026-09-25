package net.muxigame.core.compat.commands;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.towdium.jecharacters.utils.Match;
import me.towdium.pinin.PinIn;
import net.muxigame.core.nickname.Nicknames;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 指令里的玩家参数按昵称补全：打昵称的一部分、拼音或首字母，也能列出对应的 UID。
 *
 * <p>只是把原版"按前缀筛候选"放宽成"前缀或昵称"，候选还是提供方自己列的那批，插进指令的仍是 UID，
 * 服务端怎么解析、模组怎么按名字查人都不受影响。两边都生效：实体/档案参数的候选在客户端算，
 * 带自定义补全的参数（OPAC 邀请等）在服务端算，走的是同一个 suggest。
 */
public final class NicknameSuggestions {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/commands");
    private static final ThreadLocal<Boolean> COMMAND_INPUT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile boolean pinyinResolved;
    private static volatile BiPredicate<String, String> pinyin;

    private NicknameSuggestions() {}

    /** 客户端正在为补全框里的指令算候选（命令方块的指令不带 '/'，只能靠这个认出来）。返回旧值给 leave 用。 */
    public static boolean enterCommandInput() {
        boolean previous = COMMAND_INPUT.get();
        COMMAND_INPUT.set(Boolean.TRUE);
        return previous;
    }

    public static void leaveCommandInput(boolean previous) {
        COMMAND_INPUT.set(previous);
    }

    /**
     * 原版没筛中的候选，按昵称再看一眼。任何意外都当没对上，退回原版行为。
     * 每个 suggest 的每个候选都会走到这里（队伍名、计分项……），先查昵称：不是玩家就一次查表了事。
     */
    public static boolean matches(SuggestionsBuilder builder, String remaining, String candidate) {
        try {
            String nickname = Nicknames.of(candidate);
            if (nickname == null || !commandContext(builder)) return false;
            return NicknameMatch.extra(remaining, nickname, pinyin());
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /** Stream 版的 suggest 在 filter 里筛，给它换一个"原版或昵称"的条件。 */
    public static Predicate<String> widen(Predicate<? super String> vanilla, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        return candidate -> vanilla.test(candidate) || matches(builder, remaining, candidate);
    }

    /**
     * 这次 suggest 是不是在给指令算候选。普通聊天的 Tab 补全也走同一个 suggest（输入不带 '/'），
     * 那里补进去的是聊天文字，不能插 UID。专用服务端上只有指令会算补全（聊天 Tab 是纯客户端的事）。
     */
    private static boolean commandContext(SuggestionsBuilder builder) {
        return COMMAND_INPUT.get() || FMLEnvironment.dist == Dist.DEDICATED_SERVER
            || NicknameMatch.commandInput(builder.getInput());
    }

    /** 拼音借 JECh 已经建好的 PinIn（跟 JEI 搜索同一套键位设置）；没装 JECh（服务端就没装）只按子串比。 */
    private static BiPredicate<String, String> pinyin() {
        if (!pinyinResolved) {
            synchronized (NicknameSuggestions.class) {
                if (!pinyinResolved) {
                    pinyin = loadPinyin();
                    pinyinResolved = true;
                }
            }
        }
        return pinyin;
    }

    private static BiPredicate<String, String> loadPinyin() {
        LoadingModList mods = LoadingModList.get();
        if (mods == null || mods.getModFileById("jecharacters") == null) return null;
        try {
            BiPredicate<String, String> contains = Jech.contains();
            LOG.info("Nickname command completion matches pinyin via Just Enough Characters");
            return (text, query) -> {
                try {
                    return contains.test(text, query);
                } catch (RuntimeException | LinkageError error) {
                    // JECh 升级换了接口之类：以后只按子串比，不再每个候选都抛一次。
                    pinyin = null;
                    LOG.warn("Pinyin matching failed; nickname completion falls back to plain text", error);
                    return false;
                }
            };
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Just Enough Characters is installed but its PinIn is unusable; pinyin completion disabled", error);
            return null;
        }
    }

    /** 单独一个类：没装 JECh 时根本不会加载到 PinIn。 */
    private static final class Jech {
        static BiPredicate<String, String> contains() {
            PinIn context = Match.context;
            return context::contains;
        }
    }
}
