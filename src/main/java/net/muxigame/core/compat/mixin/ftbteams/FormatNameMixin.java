package net.muxigame.core.compat.mixin.ftbteams;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FTB Teams 客户端把 UUID 格式化成名字的唯一出口：队伍聊天的发言人、"我的队伍"提示里的队长。
 * 返回的是给人看的 Component，整个整合包没有别的模组调用它（已扫过）；KnownClientPlayer 里存的名字不动，
 * 服务端按它查队伍、TeamArgument 补全都还是 UID。
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.data.ClientTeamManagerImpl", remap = false)
public abstract class FormatNameMixin {
    @ModifyExpressionValue(
        method = "formatName(Ljava/util/UUID;)Lnet/minecraft/network/chat/Component;",
        at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbteams/api/client/KnownClientPlayer;name()Ljava/lang/String;"))
    private static String muxi$nickname(String name) {
        return Nicknames.display(name);
    }
}
