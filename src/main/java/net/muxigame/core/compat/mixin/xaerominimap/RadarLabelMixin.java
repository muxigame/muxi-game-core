package net.muxigame.core.compat.mixin.xaerominimap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.muxigame.core.nickname.Nicknames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 小地图雷达上玩家点旁边的名字（XaeroPlus 在整合包里强制常显）；世界地图的雷达叠层包的是同一个渲染器，一起生效。
 *
 * <p>名字来自 Misc.getFixedDisplayName → Entity.getName()，玩家就是 UID。只在画标签这一处换，
 * 公共的 getFixedDisplayName 不碰；队伍前后缀照它的写法（team.getFormattedName）重新套一遍。
 * 昵称按 GameProfile 名查，不看显示文字——别的实体恰好叫某个 UID 也不会被换。
 */
@Mixin(targets = "xaero.hud.minimap.radar.render.element.RadarRenderer", remap = false)
public abstract class RadarLabelMixin {
    @WrapOperation(
        method = "renderLabel(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;ZDLcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At(value = "INVOKE",
                 target = "Lxaero/common/misc/Misc;getFixedDisplayName(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;"))
    private Component muxi$radarNickname(Entity entity, Operation<Component> original) {
        Component label = original.call(entity);
        if (label == null || !(entity instanceof Player player)) return label;
        String nickname = Nicknames.of(player.getGameProfile().getName());
        if (nickname == null) return label;
        MutableComponent name = Component.literal(nickname);
        PlayerTeam team = entity.getTeam();
        return team == null ? name : team.getFormattedName(name);
    }
}
