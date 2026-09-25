package net.muxigame.core.compat.mixin.xaeroworldmap;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.muxigame.core.compat.maps.MapNameRules;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 世界地图"玩家"侧栏：右键菜单标题、列表项的点击宽度、筛选框。
 *
 * <p>getMenuName 本身故意不改：PlayerTeleporter.teleportToPlayer 拿它填 "/tp @s {name}"，必须还是 UID。
 * 所以只在用它显示的地方换——右键标题在这里，列表项的字在 {@link MenuEntryNameMixin}。
 */
@Mixin(targets = "xaero.map.radar.tracker.PlayerTrackerMapElementReader", remap = false)
public abstract class PlayerTrackerReaderMixin {
    /** 右键菜单第一行（标题，点了什么也不做）。 */
    @ModifyExpressionValue(
        method = "getRightClickOptions(Lxaero/map/radar/tracker/PlayerTrackerMapElement;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;",
        at = @At(value = "INVOKE",
                 target = "Lxaero/map/radar/tracker/PlayerTrackerMapElementReader;getMenuName(Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Ljava/lang/String;"))
    private String muxi$titleNickname(String name) {
        return Nicknames.display(name);
    }

    /** 列表项鼠标命中框按文字宽度算，要和画出来的昵称一样宽。 */
    @ModifyExpressionValue(
        method = "getLeftSideLength(Lxaero/map/radar/tracker/PlayerTrackerMapElement;Lnet/minecraft/client/Minecraft;)I",
        at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/GameProfile;getName()Ljava/lang/String;"))
    private String muxi$widthNickname(String login) {
        return Nicknames.display(login);
    }

    /** 筛选只拿来和输入的文字比对：昵称、UID 都能搜到。 */
    @ModifyReturnValue(method = "getFilterName(Lxaero/map/radar/tracker/PlayerTrackerMapElement;)Ljava/lang/String;", at = @At("RETURN"))
    private String muxi$filterByNickname(String name) {
        return MapNameRules.filterText(Nicknames.of(name), name);
    }
}
