package net.muxigame.core.threading.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({Entity.class,ServerPlayer.class})
public abstract class TransferBarrierMixin {
    @Inject(method="changeDimension",at=@At("HEAD"),cancellable=true)
    private void muxi$transferAtBarrier(DimensionTransition transition,CallbackInfoReturnable<Entity> callback) {
        Entity entity=(Entity)(Object)this;
        if(DimensionThreads.defer(transition.newLevel().getServer(),()->entity.changeDimension(transition))) callback.setReturnValue(null);
    }
}
