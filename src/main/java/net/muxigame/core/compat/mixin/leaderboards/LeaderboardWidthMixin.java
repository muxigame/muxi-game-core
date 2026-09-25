package net.muxigame.core.compat.mixin.leaderboards;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;

/** 排行榜界面初始化时按名字算整体宽度；和每行显示的一样按昵称量，窗口才不会比内容窄。 */
@Mixin(targets = "com.leclowndu93150.leaderboards.gui.LeaderboardScreen", remap = false)
public abstract class LeaderboardWidthMixin {
    @ModifyExpressionValue(
        method = "onInit()Z",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                 target = "Lcom/leclowndu93150/leaderboards/data/LeaderboardValue;username:Ljava/lang/String;"))
    private static String muxi$widthNickname(String name) {
        return Nicknames.display(name);
    }
}
