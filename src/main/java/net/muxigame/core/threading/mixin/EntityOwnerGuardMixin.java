package net.muxigame.core.threading.mixin;
import net.minecraft.server.level.ServerLevel;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerLevel.class)
public abstract class EntityOwnerGuardMixin {
    @Inject(method="addEntity",at=@At("HEAD"))
    private void muxi$checkEntityOwner(CallbackInfoReturnable<Boolean> callback) {
        if(DimensionThreads.onTickWorker())DimensionThreads.checkMutation((ServerLevel)(Object)this);
    }
}
