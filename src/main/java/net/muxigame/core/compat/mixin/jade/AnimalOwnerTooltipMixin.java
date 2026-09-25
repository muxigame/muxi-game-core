package net.muxigame.core.compat.mixin.jade;

import net.muxigame.core.compat.displays.DisplayNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Jade 驯服生物的"主人：%s"。
 *
 * <p>名字有两条来路：客户端不知道主人 UUID 时由服务端（也装了 Jade）查好流过来，知道时（狼、猫等）
 * 客户端自己调 CommonProxy.getLastKnownUsername。两条路都汇到这一次 translatable，所以只换这里的参数，
 * 流过来的数据和查名字的方法都不动；查不到时 Jade 显示的 "???" 不是登录名，原样放过。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.AnimalOwnerProvider", remap = false)
public abstract class AnimalOwnerTooltipMixin {
    @ModifyArg(
        method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/EntityAccessor;Lsnownee/jade/api/config/IPluginConfig;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
        index = 1)
    private static Object[] muxi$ownerNickname(Object[] args) {
        return DisplayNames.args(args);
    }
}
