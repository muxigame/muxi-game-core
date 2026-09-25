package net.muxigame.core.feature.spawning;

/** 刷怪类别过滤的纯规则，不碰 Minecraft，方便单测。 */
public final class SpawnCategoryRules {
    private SpawnCategoryRules() {}

    /** 整个维度都不碰的命名空间：暮色森林靠事件在结构里加刷怪，数据里看不出来。 */
    public static boolean leftAlone(String dimensionNamespace) {
        return "twilightforest".equals(dimensionNamespace);
    }

    /**
     * 哪些类别跳过：不可能刷出东西的跳过。MONSTER 永远不跳——女仆模组的妖精是用事件加进 MONSTER 的，
     * 数据里看不到；MISC 原版本来就不参与自然刷怪，不用管。
     */
    public static boolean[] toSkip(boolean[] possible, int monster, int misc) {
        boolean[] skip = new boolean[possible.length];
        for (int i = 0; i < possible.length; i++) skip[i] = !possible[i] && i != monster && i != misc;
        return skip;
    }
}
