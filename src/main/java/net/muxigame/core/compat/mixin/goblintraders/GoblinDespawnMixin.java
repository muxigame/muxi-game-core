package net.muxigame.core.compat.mixin.goblintraders;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Timed natural goblins must leave even when Villager Names gives every merchant a custom name. */
@Pseudo
@Mixin(targets="com.mrcrayfish.goblintraders.entity.AbstractGoblinEntity",remap=false)
public abstract class GoblinDespawnMixin {
    @Shadow(remap=false) private int despawnDelay;
    @Shadow(remap=false) public abstract boolean hasCustomer();

    @Inject(method="baseTick",at=@At("TAIL"),remap=false)
    private void muxi$discardExpiredNaturalTrader(CallbackInfo ci) {
        Mob goblin=(Mob)(Object)this;
        if(despawnDelay==0 && !hasCustomer() && !goblin.isLeashed()) ((Entity)goblin).discard();
    }
}
