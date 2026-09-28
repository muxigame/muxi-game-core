package net.muxigame.core.mixin;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.challenge.ChallengeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Per-player only: no world gamerule mutation and no natural regeneration bypassing healing potions. */
@Mixin(FoodData.class)
public abstract class ChallengeHungerMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void muxi$fixedHunger(Player player,CallbackInfo ci){if(player instanceof ServerPlayer p&&ChallengeFeature.locked(p)){var food=p.getFoodData();food.setFoodLevel(20);food.setSaturation(0);food.setExhaustion(0);ci.cancel();}}
}
