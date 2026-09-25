package net.muxigame.core;

import net.muxigame.core.compat.displays.DisplayNames;
import java.util.Arrays;
import java.util.Map;
import java.util.function.UnaryOperator;

/** 其他显示位置兼容的纯逻辑测试；由 CoreSelfTest 调用，返回通过的条数。 */
public final class DisplaysSelfTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }

    public static int run() {
        passed = 0;
        Map<String, String> nicks = Map.of("10000", "洛可", "10002", "10003");
        // 和 Nicknames.display 同样的约定：有昵称给昵称，没有给原值。
        UnaryOperator<String> display = name -> nicks.getOrDefault(name, name);

        Object[] owner = {"10000"};
        Object[] shown = DisplayNames.mapStrings(owner, display);
        check("login arg becomes nickname", "洛可".equals(shown[0]));
        check("caller's array untouched", "10000".equals(owner[0]));

        Object[] unknown = {"10001"};
        check("no nickname keeps same array", DisplayNames.mapStrings(unknown, display) == unknown);

        Object[] mixed = {5L, "10000", "洛可"};
        Object[] mixedShown = DisplayNames.mapStrings(mixed, display);
        check("only exact login strings change", Arrays.equals(mixedShown, new Object[] {5L, "洛可", "洛可"}));

        // 整段精确匹配：带前后缀的文字不是登录名。
        Object[] framed = {"10000's Party", " 10000"};
        check("no partial match", DisplayNames.mapStrings(framed, display) == framed);

        // 昵称恰好是数字也只换一层，不会拿昵称再去查一遍。
        check("single hop", "10003".equals(DisplayNames.mapStrings(new Object[] {"10002"}, display)[0]));

        check("null args", DisplayNames.mapStrings(null, display) == null);
        Object[] empty = {};
        check("empty args", DisplayNames.mapStrings(empty, display) == empty);
        Object[] withNull = {null, "10000"};
        check("null element skipped", Arrays.equals(DisplayNames.mapStrings(withNull, display), new Object[] {null, "洛可"}));
        check("mapper null means keep", DisplayNames.mapStrings(owner, name -> null) == owner);

        // 测试进程里没有 FML，Simple Nicknames 视为不可用：真实入口必须原样放行。
        check("no Simple Nicknames passes through", DisplayNames.args(owner) == owner);
        return passed;
    }

    public static void main(String[] args) {
        System.out.println("DisplaysSelfTest: " + run() + " passed");
    }
}
