package net.muxigame.core.taskssmoke.mixin;

import net.muxigame.core.client.challenge.ChallengeClient;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Synthetic native item icons for the no-world render fixture; production reads the player's inventory. */
@Mixin(value=ChallengeClient.class,remap=false)
public abstract class WeaponIconFixtureMixin {
    @Inject(method="weaponIcon",at=@At("HEAD"),cancellable=true)
    private static void fixture(int slot,CallbackInfoReturnable<ItemStack> ci){
        ci.setReturnValue(new ItemStack(slot==5?Items.CROSSBOW:Items.BOW));
    }
}
