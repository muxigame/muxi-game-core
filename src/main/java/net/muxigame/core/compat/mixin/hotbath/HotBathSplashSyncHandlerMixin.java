package net.muxigame.core.compat.mixin.hotbath;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defer cosmetic splash sampling while an entity's full chunk is unavailable. */
@Pseudo
@Mixin(targets="com.crabmod.hotbath.events.SplashSyncHandler", remap=false)
public abstract class HotBathSplashSyncHandlerMixin {
    @Inject(method="onEntityTick", at=@At("HEAD"), cancellable=true, remap=false, require=1)
    private static void muxi$sampleOnlyReadyChunks(EntityTickEvent.Post event, CallbackInfo ci) {
        var entity=event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        var position=entity.blockPosition();
        // getChunkNow does not request, generate, or wait for a full chunk.
        // Both HotBath samples (feet and below) share this horizontal chunk.
        if (level.getChunkSource().getChunkNow(position.getX()>>4, position.getZ()>>4)==null) {
            ci.cancel();
        }
    }
}
