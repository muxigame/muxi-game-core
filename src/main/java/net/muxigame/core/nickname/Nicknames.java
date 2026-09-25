package net.muxigame.core.nickname;

import com.donutello.simplenicknames.nickname.NicknameFormatter;
import com.donutello.simplenicknames.nickname.NicknameManager;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录名（平台 UID）→ 昵称，只给"显示"用。
 *
 * <p>身份、指令插入的文本、存档里的名字、皮肤键一律仍是 UID：这里只在把名字画到屏幕上、拼进给人看的文字的那一刻替换。
 * 昵称来自 Simple Nicknames：专用服务端上是服务端那份表，客户端上是服务端同步下来的整张表（含离线玩家），
 * 两边都挂在 {@code NicknameFormatter.FORMATTING_MANAGER} 上，所以同一段代码两边都能用。
 * 表按登录名精确匹配；昵称允许重名，别拿昵称反查身份。
 */
public final class Nicknames {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/nicknames");
    private static volatile Boolean available;

    private Nicknames() {}

    /** Simple Nicknames 在不在。查一次记住；调用失败（模组升级改了接口）就永久关掉，退回显示 UID。 */
    public static boolean available() {
        Boolean known = available;
        if (known == null) {
            LoadingModList mods = LoadingModList.get();
            known = mods != null && mods.getModFileById("simplenicknames") != null;
            available = known;
        }
        return known;
    }

    /** 这个登录名的昵称；没有昵称、昵称和登录名一样、或 Simple Nicknames 不可用时返回 null。 */
    public static String of(String loginName) {
        if (loginName == null || loginName.isEmpty() || !available()) return null;
        try {
            NicknameManager manager = NicknameFormatter.FORMATTING_MANAGER;
            String nickname = manager == null ? null : manager.getRawNickname(loginName);
            return nickname == null || nickname.isBlank() || nickname.equals(loginName) ? null : nickname;
        } catch (LinkageError | RuntimeException error) {
            available = false;
            LOG.warn("Simple Nicknames lookup failed; showing login names from now on", error);
            return null;
        }
    }

    /** 有昵称显示昵称，没有就原样返回登录名。 */
    public static String display(String loginName) {
        String nickname = of(loginName);
        return nickname == null ? loginName : nickname;
    }

    /**
     * 整段文字恰好是一个登录名时换成昵称，保留原来的样式；否则原样返回。
     * 只适合"这段 Component 就是名字本身"的地方——带了队伍前后缀的已经不是精确的登录名，不会被换。
     */
    public static Component display(Component name) {
        if (name == null) return null;
        String nickname = of(name.getString());
        return nickname == null ? name : Component.literal(nickname).withStyle(name.getStyle());
    }
}
