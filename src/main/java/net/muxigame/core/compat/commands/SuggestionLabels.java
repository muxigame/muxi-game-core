package net.muxigame.core.compat.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.muxigame.core.nickname.Nicknames;
import java.util.UUID;

/**
 * 客户端：补全框每一行"画出来的字"。只在客户端 mixin 里用。
 *
 * <p>补全行的文字同时也是插进输入框的文字（还是 Chat Heads 按名字找头像的键），所以不能改候选本身，
 * 只改画的那一下：UID 后面跟上昵称。原版的提示框只在鼠标悬停时出现，只用键盘的人看不到，所以直接画在行里。
 */
public final class SuggestionLabels {
    private SuggestionLabels() {}

    public static String label(String text) {
        if (text == null || text.isEmpty() || !Nicknames.available()) return text;
        try {
            String nickname = Nicknames.of(text);
            if (nickname == null && NicknameMatch.looksLikeUuid(text)) nickname = byUuid(text);
            return NicknameMatch.label(text, nickname);
        } catch (RuntimeException error) {
            return text;
        }
    }

    /** 实体参数会把准星指着的实体按 UUID 列出来；是在线玩家就查出他的登录名再换昵称。 */
    private static String byUuid(String text) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return null;
        PlayerInfo info = connection.getPlayerInfo(UUID.fromString(text));
        return info == null ? null : Nicknames.of(info.getProfile().getName());
    }
}
