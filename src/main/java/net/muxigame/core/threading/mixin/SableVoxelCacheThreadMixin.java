package net.muxigame.core.threading.mixin;

import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMaps;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable shares both collision predicates across worlds. Protect map operations, not shape evaluation. */
@Pseudo
@Mixin(targets={"dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState$1",
    "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState$2"},remap=false)
public abstract class SableVoxelCacheThreadMixin {
    @Shadow(remap=false) @Final @Mutable private Reference2BooleanMap<BlockState> cache;

    @Inject(method="<init>",at=@At("RETURN"))
    private void muxi$protectSharedCache(CallbackInfo callback) {
        // Preserve identity keys and boolean semantics. No removals occur, so separate
        // contains/get calls are safe; concurrent misses may harmlessly compute twice.
        cache=Reference2BooleanMaps.synchronize(cache);
    }
}
