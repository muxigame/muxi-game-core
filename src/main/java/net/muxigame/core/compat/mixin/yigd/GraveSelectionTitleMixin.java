package net.muxigame.core.compat.mixin.yigd;

import net.muxigame.core.compat.displays.DisplayNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 坟墓列表的标题"%s 的坟墓"。标题在调用父类构造之前就拼好了，处理方法必须是 static。
 * 列表按服务端给的 GraveData 走，不看这个名字。
 */
@Mixin(targets = "com.b1n_ry.yigd.client.gui.GraveSelectionScreen", remap = false)
public abstract class GraveSelectionTitleMixin {
    @ModifyArg(
        method = "<init>(Ljava/util/List;Lnet/minecraft/world/item/component/ResolvableProfile;Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
        index = 1)
    private static Object[] muxi$titleNickname(Object[] args) {
        return DisplayNames.args(args);
    }
}
