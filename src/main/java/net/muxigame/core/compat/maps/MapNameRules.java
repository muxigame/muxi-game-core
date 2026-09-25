package net.muxigame.core.compat.maps;

/** 地图兼容里不依赖 Minecraft 的小规则，单独拿出来好测。 */
public final class MapNameRules {
    private MapNameRules() {}

    /**
     * 世界地图玩家列表"筛选玩家"框比对的文字：昵称在前，原文（UID）留在后面。
     * Xaero 先把开头就匹配的排到前面、再收包含匹配的，所以按昵称开头优先，按 UID 也照样搜得到。
     * 没有昵称就原样返回。
     */
    public static String filterText(String nickname, String original) {
        if (nickname == null || nickname.isEmpty() || original == null) return original;
        return nickname + " " + original;
    }
}
