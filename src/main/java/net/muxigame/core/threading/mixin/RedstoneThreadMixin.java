package net.muxigame.core.threading.mixin;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneThreadMixin {
    @Unique private final ThreadLocal<Boolean> muxi$signal=ThreadLocal.withInitial(()->true);
    @Redirect(method="calculateTargetStrength",at=@At(value="FIELD",target="Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldSignal:Z",opcode=181))
    private void muxi$setSignal(RedStoneWireBlock block,boolean value){muxi$signal.set(value);}
    @Redirect(method={"getDirectSignal","getSignal","isSignalSource"},at=@At(value="FIELD",target="Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldSignal:Z",opcode=180))
    private boolean muxi$getSignal(RedStoneWireBlock block){return muxi$signal.get();}
}
