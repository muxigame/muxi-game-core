package net.muxigame.core.compat.mixin.leaderboards;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;

/**
 * 排行榜一行里的玩家名。服务端发来的是 UID（离线玩家也是），客户端的昵称表含离线玩家，所以在客户端换就够了。
 * 构造里量列宽、draw 里画字，两处都得换，不然列宽按 UID 算、昵称长了会压到数值列。
 * LeaderboardValue.username 本身不改：搜索框的过滤文字仍取它。
 */
@Mixin(targets = "com.leclowndu93150.leaderboards.gui.LeaderboardScreen$LeaderboardEntry", remap = false)
public abstract class LeaderboardEntryMixin {
    @ModifyExpressionValue(
        method = {
            "<init>(Lcom/leclowndu93150/leaderboards/gui/LeaderboardScreen;Ldev/ftb/mods/ftblibrary/ui/Panel;Lcom/leclowndu93150/leaderboards/data/LeaderboardValue;)V",
            "draw(Lnet/minecraft/client/gui/GuiGraphics;Ldev/ftb/mods/ftblibrary/ui/Theme;IIII)V"
        },
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                 target = "Lcom/leclowndu93150/leaderboards/data/LeaderboardValue;username:Ljava/lang/String;"))
    private static String muxi$rowNickname(String name) {
        return Nicknames.display(name);
    }
}
