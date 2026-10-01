package net.muxigame.core.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.muxigame.core.feature.champions.ChampionRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Champions 21.1.1.7 的 isEligible 只看"是不是 Mob"：鳕鱼、飞鱼、村民、猫都会被加词条，死了还按强敌掉锭。
 * 它没有任何配置、标签或数据包能收窄这个范围（allow_champions 标签只给词条用），只能在这里补上。
 */
@Mixin(targets = "top.theillusivec4.champions.common.champion.ChampionSpawnHandler", remap = false)
public abstract class ChampionSpawnHandlerMixin {
    @Inject(method = "isEligible", at = @At("HEAD"), cancellable = true)
    private static void muxi$hostileOnly(LivingEntity entity, CallbackInfoReturnable<Boolean> result) {
        if (net.muxigame.minigames.GameAreas.contains(entity.level()) || !ChampionRules.mayBeChampion(entity)) result.setReturnValue(false);
    }
}
