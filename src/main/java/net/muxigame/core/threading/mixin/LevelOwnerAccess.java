package net.muxigame.core.threading.mixin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Level.class)
public interface LevelOwnerAccess {
    @Accessor("thread") Thread muxi$getOwner();
    @Mutable @Accessor("thread") void muxi$setOwner(Thread owner);
}
