package net.muxigame.core.compat.displays;

import net.muxigame.core.nickname.Nicknames;
import java.util.function.UnaryOperator;

/**
 * 各模组把名字塞进 {@code Component.translatable(key, args...)} 的地方，统一在参数这一步换成昵称。
 *
 * <p>只换恰好是登录名的字符串参数，数字、Component 和别的文字原样不动；一个都没换就返回原数组，
 * 换了就返回副本——参数数组虽然都是现场 new 出来的，也不假设调用方之后不再读它。
 */
public final class DisplayNames {
    private DisplayNames() {}

    /** 翻译参数里的登录名换成昵称。 */
    public static Object[] args(Object[] args) {
        return mapStrings(args, Nicknames::display);
    }

    /** 与 Minecraft 无关的那部分，单独拿出来测。{@code display} 返回 null 视为"不换"。 */
    public static Object[] mapStrings(Object[] args, UnaryOperator<String> display) {
        if (args == null) return null;
        Object[] out = args;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof String name)) continue;
            String shown = display.apply(name);
            if (shown == null || shown.equals(name)) continue;
            if (out == args) out = args.clone();
            out[i] = shown;
        }
        return out;
    }
}
