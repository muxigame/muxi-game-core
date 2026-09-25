package net.muxigame.core;

import net.muxigame.core.compat.commands.NicknameMatch;
import net.muxigame.core.feature.chat.CompletionDiff;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** 指令补全 / 聊天补全兼容的纯逻辑测试；由 CoreSelfTest 调用，返回通过的条数。 */
public final class CommandsSelfTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }

    /** 假拼音：只认这几组，别的一律不认；用来确认什么时候会去问拼音。 */
    private static final BiPredicate<String, String> PINYIN = (text, query) ->
        (text.equals("张三") && (query.equals("zs") || query.equals("zhangsan")))
            || (text.equals("李四") && query.equals("ls"));
    private static final BiPredicate<String, String> NEVER_ASK = (text, query) -> {
        throw new AssertionError("pinyin should not be consulted for '" + query + "'");
    };

    /** 按 mixin 里的判法走一遍候选：原版前缀筛中，或者昵称对得上；每个候选只判一次、只收一次。 */
    private static List<String> suggest(List<String> candidates, String remaining, Map<String, String> nicknames,
                                        BiPredicate<String, String> pinyin) {
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            boolean vanilla = candidate.startsWith(remaining.toLowerCase());
            if (vanilla || NicknameMatch.extra(remaining, nicknames.get(candidate), pinyin)) result.add(candidate);
        }
        return result;
    }

    public static int run() {
        passed = 0;

        // ---- 输入规整 ----
        check("query plain", "张".equals(NicknameMatch.query("张")));
        check("query open quote", "张".equals(NicknameMatch.query("\"张")));
        check("query closed quote", "张三".equals(NicknameMatch.query("\"张三\"")));
        check("query single quote", "张三".equals(NicknameMatch.query("'张三'")));
        check("query lower-cased", "zs".equals(NicknameMatch.query("ZS")));
        check("query keeps inner space", "洛可 t".equals(NicknameMatch.query("\"洛可 t")));
        check("empty query", NicknameMatch.query("") == null);
        check("blank query", NicknameMatch.query("   ") == null);
        check("lone quote", NicknameMatch.query("\"") == null);
        check("null query", NicknameMatch.query(null) == null);

        // ---- 昵称匹配 ----
        check("prefix", NicknameMatch.matches("张三", "张", null));
        check("substring, not only prefix", NicknameMatch.matches("张三", "三", null));
        check("case-insensitive", NicknameMatch.matches("Roc洛可", NicknameMatch.query("ROC"), null));
        check("no match", !NicknameMatch.matches("张三", "李", null));
        check("no nickname", !NicknameMatch.extra("张", null, PINYIN));
        check("empty input never adds", !NicknameMatch.extra("", "张三", PINYIN));
        check("pinyin initials", NicknameMatch.extra("zs", "张三", PINYIN));
        check("pinyin full", NicknameMatch.extra("ZhangSan", "张三", PINYIN));
        check("pinyin unavailable", !NicknameMatch.extra("zs", "张三", null));
        check("pinyin wrong", !NicknameMatch.extra("ww", "张三", PINYIN));
        // 纯数字是在打 UID、纯汉字子串已经比过：都不该去问拼音（NEVER_ASK 被调用就会抛）。
        check("digits skip pinyin", !NicknameMatch.extra("100", "张三", NEVER_ASK));
        check("hanzi skip pinyin", !NicknameMatch.extra("李", "张三", NEVER_ASK));
        check("substring before pinyin", NicknameMatch.extra("roc", "Roc洛可", NEVER_ASK));

        // ---- 走一遍候选 ----
        List<String> online = List.of("10000", "10001", "20000");
        Map<String, String> nick = Map.of("10000", "张三", "10001", "李四", "20000", "王五");
        check("UID prefix is vanilla's job", suggest(online, "100", nick, NEVER_ASK).equals(List.of("10000", "10001")));
        check("nickname finds its UID", suggest(online, "李", nick, PINYIN).equals(List.of("10001")));
        check("quoted nickname finds its UID", suggest(online, "\"王五\"", nick, PINYIN).equals(List.of("20000")));
        check("pinyin finds its UID", suggest(online, "zs", nick, PINYIN).equals(List.of("10000")));
        check("nothing matches", suggest(online, "赵", nick, PINYIN).isEmpty());
        check("empty input lists everyone", suggest(online, "", nick, NEVER_ASK).equals(online));
        // 昵称可以重名：两个人都列出来，由玩家按 UID 选。
        Map<String, String> twins = Map.of("10000", "张三", "10001", "张三");
        check("duplicate nicknames both offered", suggest(online, "张三", twins, null).equals(List.of("10000", "10001")));
        // 前缀和昵称都对得上的候选也只收一次。
        Map<String, String> numeric = Map.of("10000", "10000号", "10001", "李四");
        check("no duplicate when both match", suggest(online, "100", numeric, null).equals(List.of("10000", "10001")));
        check("candidate without nickname untouched", suggest(online, "王", Map.of("10000", "张三"), PINYIN).isEmpty());

        // ---- 指令与普通聊天 ----
        check("chat command", NicknameMatch.commandInput("/msg 张"));
        check("plain chat", !NicknameMatch.commandInput("hello 张"));
        check("command block without slash needs the marker", !NicknameMatch.commandInput("tp 张"));
        check("null input", !NicknameMatch.commandInput(null));

        // ---- 补全行显示 ----
        check("label with nickname", "10000 张三".equals(NicknameMatch.label("10000", "张三")));
        check("label without nickname", "10000".equals(NicknameMatch.label("10000", null)));
        check("label blank nickname", "10000".equals(NicknameMatch.label("10000", "  ")));
        check("label strips formatting", "10000 张三".equals(NicknameMatch.label("10000", "§a张三")));
        check("label same as text", "10000".equals(NicknameMatch.label("10000", "10000")));
        check("uuid shape", NicknameMatch.looksLikeUuid("bac84aa3-61d5-3a42-acb0-02ab44e17ee2"));
        check("uid is not uuid", !NicknameMatch.looksLikeUuid("10000"));
        check("uuid needs dashes", !NicknameMatch.looksLikeUuid("bac84aa361d53a42acb002ab44e17ee2xxxx"));
        check("uuid needs hex", !NicknameMatch.looksLikeUuid("zac84aa3-61d5-3a42-acb0-02ab44e17ee2"));

        // ---- 聊天补全表记账 ----
        Map<String, String> table = Map.of("10000", "张三", "10001", "张三", "10002", "李四", "10003", " ");
        Set<String> all = CompletionDiff.nicknames(List.of("10000", "10001", "10002", "10003", "10004"), table::get);
        check("nicknames deduped, blanks and missing skipped", all.equals(Set.of("张三", "李四")));
        Set<String> oneTwinLeft = CompletionDiff.nicknames(List.of("10001", "10002"), table::get);
        check("shared nickname stays while a holder is online", CompletionDiff.removed(all, oneTwinLeft).isEmpty());
        Set<String> onlyLi = CompletionDiff.nicknames(List.of("10002"), table::get);
        check("last holder leaving removes it", CompletionDiff.removed(all, onlyLi).equals(List.of("张三")));
        check("nothing added when shrinking", CompletionDiff.added(all, onlyLi).isEmpty());
        check("join adds", CompletionDiff.added(onlyLi, all).equals(List.of("张三")));
        check("no change no diff", CompletionDiff.added(all, all).isEmpty() && CompletionDiff.removed(all, all).isEmpty());
        check("everything removed when nicknames unavailable",
              CompletionDiff.removed(all, CompletionDiff.nicknames(List.of("10000"), login -> null)).size() == 2);
        return passed;
    }

    public static void main(String[] args) {
        System.out.println("CommandsSelfTest: " + run() + " passed");
    }
}
