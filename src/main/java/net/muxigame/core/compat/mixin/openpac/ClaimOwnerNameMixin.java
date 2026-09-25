package net.muxigame.core.compat.mixin.openpac;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 领地默认名"某某的领地"里的某某。
 *
 * <p>客户端：Xaero 小地图/世界地图悬停领地时的提示；服务端：走进别人领地时的动作栏提示和领地指令的回执。
 * 两边都经 getDefaultName → 这里拼字，拼好的只拿去显示，不存档、不当键。存下来的 playerUsername 仍是 UID
 * （OPAC 的指令、存档靠它），所以只换这一次取出来拼字的值，getter 本身不碰。
 * 客户端查服务端同步来的整表（含离线玩家），服务端查自己的表，同一段代码两边都对。
 */
@Mixin(targets = "xaero.pac.common.claims.ClaimsManager", remap = false)
public abstract class ClaimOwnerNameMixin {
    @ModifyExpressionValue(
        method = "constructPlayerClaimName(Lxaero/pac/common/claims/player/PlayerClaimInfo;Lnet/minecraft/network/chat/Component;Z)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lxaero/pac/common/claims/player/PlayerClaimInfo;getPlayerUsername()Ljava/lang/String;"))
    private String muxi$ownerNickname(String login) {
        return Nicknames.display(login);
    }
}
