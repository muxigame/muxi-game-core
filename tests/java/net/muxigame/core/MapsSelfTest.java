package net.muxigame.core;

import net.muxigame.core.compat.maps.MapNameRules;

/** Xaero 地图 / 领地显示兼容的纯逻辑测试；由 CoreSelfTest 调用，返回通过的条数。 */
public final class MapsSelfTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }

    public static int run() {
        passed = 0;
        String uid = "10000";
        String uuidFallback = "123e4567-e89b-12d3-a456-426614174000";

        // 没有昵称：筛选文字和原来一模一样（连对象都不换）。
        check("no nickname keeps original", MapNameRules.filterText(null, uid) == uid);
        check("blank nickname keeps original", MapNameRules.filterText("", uid) == uid);
        check("PlayerInfo missing keeps uuid text", MapNameRules.filterText(null, uuidFallback) == uuidFallback);
        check("null original stays null", MapNameRules.filterText("小明", null) == null);

        // 有昵称：昵称在前、UID 仍在。
        String text = MapNameRules.filterText("小明", uid);
        check("nickname then uid", "小明 10000".equals(text));
        check("starts with nickname (Xaero ranks prefix matches first)", text.startsWith("小明"));
        check("uid still searchable", text.contains(uid));
        check("latin nickname matches after Xaero lowercases", MapNameRules.filterText("Steve", uid).toLowerCase().startsWith("steve"));
        check("nickname with spaces kept whole", "小 明 10000".equals(MapNameRules.filterText("小 明", uid)));
        return passed;
    }

    public static void main(String[] args) {
        System.out.println("MapsSelfTest: " + run() + " passed");
    }
}
