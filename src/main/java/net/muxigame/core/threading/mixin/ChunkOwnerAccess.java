package net.muxigame.core.threading.mixin;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerChunkCache.class)
public interface ChunkOwnerAccess {
    @Accessor("mainThread") Thread muxi$getOwner();
    @Mutable @Accessor("mainThread") void muxi$setOwner(Thread owner);
}
