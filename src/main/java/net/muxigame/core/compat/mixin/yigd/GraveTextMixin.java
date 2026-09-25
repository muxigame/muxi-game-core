package net.muxigame.core.compat.mixin.yigd;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 坟墓上方浮着的主人名。
 *
 * <p>这段字是死亡那一刻用 UID 生成、存进方块实体 NBT 的，所以在画的时候换：已经存在的老坟也跟着显示昵称，
 * 存档里仍是 UID。宽度和缩放是拿换过的文字现算的，昵称长短不同也能居中。
 */
@Mixin(targets = "com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer", remap = false)
public abstract class GraveTextMixin {
    @ModifyExpressionValue(
        method = "renderGraveText(Lcom/b1n_ry/yigd/block/entity/GraveBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE",
                 target = "Lcom/b1n_ry/yigd/block/entity/GraveBlockEntity;getGraveText()Lnet/minecraft/network/chat/Component;"))
    private static Component muxi$graveOwnerNickname(Component text) {
        return Nicknames.display(text);
    }
}
