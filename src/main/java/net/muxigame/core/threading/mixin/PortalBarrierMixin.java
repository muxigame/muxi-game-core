package net.muxigame.core.threading.mixin;
import net.minecraft.world.entity.*;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class PortalBarrierMixin {
    @Shadow public PortalProcessor portalProcess;
    @Shadow protected abstract void handlePortal();
    @Inject(method="handlePortal",at=@At("HEAD"),cancellable=true)
    private void muxi$portalAtBarrier(CallbackInfo callback) {
        var entity=(Entity)(Object)this;
        if(portalProcess!=null && entity.getServer()!=null && DimensionThreads.defer(entity.getServer(),this::handlePortal)) callback.cancel();
    }
}
