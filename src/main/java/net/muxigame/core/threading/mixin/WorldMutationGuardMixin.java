package net.muxigame.core.threading.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Level.class)
public abstract class WorldMutationGuardMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"))
    private void muxi$singleWriter(CallbackInfoReturnable<Boolean> callback) {
        if((Object)this instanceof ServerLevel level)DimensionThreads.checkMutation(level);
    }
}
