package net.muxigame.core.compat.commands;

import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * 指令补全里"按昵称找 UID"和"补全行显示昵称"的纯逻辑，不碰 Minecraft，方便单测。
 *
 * <p>这里只回答"对不对得上""画成什么字"；插进指令的永远是候选本身（UID），不在这里决定。
 */
public final class NicknameMatch {
    private NicknameMatch() {}

    /**
     * 正在打的那个词，转成可比较的形式：去掉引号（中文昵称在实体参数里得加引号才能输）、首尾空白，转小写。
     * 没有可比的内容返回 null——空输入时原版已经列出全部候选，用不着这里补。
     */
    public static String query(String remaining) {
        if (remaining == null) return null;
        String text = remaining.strip();
        if (!text.isEmpty() && (text.charAt(0) == '"' || text.charAt(0) == '\'')) {
            char quote = text.charAt(0);
            text = text.substring(1);
            if (text.endsWith(String.valueOf(quote))) text = text.substring(0, text.length() - 1);
            text = text.strip();
        }
        return text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
    }

    /**
     * 昵称对不对得上已经 {@link #query 规整过} 的输入：先按子串比（不分大小写），再交给拼音（可为 null）。
     * 拼音只在输入里有英文字母时才问：纯数字是在打 UID，纯汉字子串已经比过了。
     */
    public static boolean matches(String nickname, String query, BiPredicate<String, String> pinyin) {
        if (nickname == null || query == null || query.isEmpty()) return false;
        String text = nickname.toLowerCase(Locale.ROOT);
        if (text.contains(query)) return true;
        return pinyin != null && hasAsciiLetter(query) && pinyin.test(text, query);
    }

    /** 原版前缀没筛中的候选要不要补上：输入规整后能对上这个候选的昵称。 */
    public static boolean extra(String remaining, String nickname, BiPredicate<String, String> pinyin) {
        return nickname != null && matches(nickname, query(remaining), pinyin);
    }

    /**
     * 补全框里的输入是不是指令。聊天框里的指令总以 '/' 开头；不带 '/' 的是普通聊天的 Tab 补全，
     * 那里补进去的是聊天文字，把"张"换成 UID 就是乱改玩家要说的话。命令方块不带 '/'，由调用方另行标记。
     */
    public static boolean commandInput(String input) {
        return input != null && input.startsWith("/");
    }

    /** 补全行画出来的字：有昵称画"UID 昵称"，没有就原样。昵称里的 § 格式码去掉，免得把后半行染色。 */
    public static String label(String text, String nickname) {
        if (text == null || nickname == null || nickname.isBlank()) return text;
        String clean = stripFormatting(nickname).strip();
        return clean.isEmpty() || clean.equals(text) ? text : text + " " + clean;
    }

    /** 看起来是不是 UUID 字符串（实体参数会把准星指着的实体按 UUID 列出来）；先比形状，省得每帧抛异常。 */
    public static boolean looksLikeUuid(String text) {
        if (text == null || text.length() != 36) return false;
        for (int i = 0; i < 36; i++) {
            char c = text.charAt(i);
            boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
            if (dash ? c != '-' : Character.digit(c, 16) < 0) return false;
        }
        return true;
    }

    private static boolean hasAsciiLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    private static String stripFormatting(String text) {
        if (text.indexOf('§') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') i++;
            else out.append(c);
        }
        return out.toString();
    }
}
