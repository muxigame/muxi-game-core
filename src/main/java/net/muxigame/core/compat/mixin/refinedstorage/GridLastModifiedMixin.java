package net.muxigame.core.compat.mixin.refinedstorage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 网格物品提示里的"最后由 %s 修改于……"。来源名是服务端记的 PlayerActor 登录名，同步下来后客户端只在这里用
 * （"刚刚"和"N 分钟前"两个分支各取一次）。机器来源（NetworkNodeActor）记的是类名，不是登录名，原样显示。
 */
@Mixin(targets = "com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen", remap = false)
public abstract class GridLastModifiedMixin {
    @ModifyExpressionValue(
        method = "getLastModifiedText(Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedResource;)Lnet/minecraft/network/chat/MutableComponent;",
        at = @At(value = "INVOKE", target = "Lcom/refinedmods/refinedstorage/api/storage/tracked/TrackedResource;getSourceName()Ljava/lang/String;"))
    private static String muxi$modifiedByNickname(String name) {
        return Nicknames.display(name);
    }
}
