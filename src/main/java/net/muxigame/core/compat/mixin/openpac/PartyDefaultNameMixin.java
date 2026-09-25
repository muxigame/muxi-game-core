package net.muxigame.core.compat.mixin.openpac;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * OPAC 队伍没起名时的默认名"某某's Party"里的某某（队伍聊天前缀的悬停、队伍指令回执、同盟列表、队伍界面）。
 *
 * <p>默认名每次现拼，不存档；用到它的地方全是显示（发消息、悬停、同步给客户端显示的同盟名），
 * 没有拿它比对或当指令参数的。队长的 username 字段仍是 UID，这里只换拼进这句话的那一次读取。
 */
@Mixin(targets = "xaero.pac.common.parties.party.Party", remap = false)
public abstract class PartyDefaultNameMixin {
    @ModifyExpressionValue(
        method = "getDefaultName()Ljava/lang/String;",
        at = @At(value = "INVOKE", target = "Lxaero/pac/common/parties/party/member/PartyMember;getUsername()Ljava/lang/String;"))
    private String muxi$ownerNickname(String login) {
        return Nicknames.display(login);
    }
}
