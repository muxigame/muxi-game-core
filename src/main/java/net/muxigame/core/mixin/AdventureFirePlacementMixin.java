package net.muxigame.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.muxigame.core.feature.rules.AdventureEnvironmentProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared write path covers ignition by tools, projectiles, lightning, lava and standard mod fire. */
@Mixin(value=Level.class, remap=false)
public abstract class AdventureFirePlacementMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at=@At("HEAD"), cancellable=true)
    private void muxi$rejectAdventureFire(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> result) {
        if (AdventureEnvironmentProtection.rejectFire((Level)(Object)this, state)) result.setReturnValue(false);
    }
}
