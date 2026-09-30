package net.muxigame.core.threading.mixin;
import net.minecraft.server.level.*;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerChunkCache.class)
public abstract class ChunkGuardMixin {
    @Shadow @Final public ServerLevel level;
    @Inject(method={"getChunk","getChunkNow","getChunkFuture"},at=@At("HEAD"))
    private void muxi$checkOwner(CallbackInfoReturnable<?> callback) { DimensionThreads.checkChunkOwner(level); }
}
