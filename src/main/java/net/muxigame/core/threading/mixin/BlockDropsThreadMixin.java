package net.muxigame.core.threading.mixin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
/** NeoForge captures block drops in a static list; concurrent worlds require separate scopes. */
@Mixin(Block.class)
public abstract class BlockDropsThreadMixin {
    @Unique private static final ThreadLocal<Deque<List<ItemEntity>>> muxi$drops=ThreadLocal.withInitial(ArrayDeque::new);
    @Inject(method="beginCapturingDrops",at=@At("HEAD"),cancellable=true)
    private static void muxi$beginDrops(CallbackInfo callback) { muxi$drops.get().push(new ArrayList<>()); callback.cancel(); }
    @Inject(method="stopCapturingDrops",at=@At("HEAD"),cancellable=true)
    private static void muxi$endDrops(CallbackInfoReturnable<List<ItemEntity>> callback) {
        var scopes=muxi$drops.get(); var drops=scopes.pop(); if(scopes.isEmpty())muxi$drops.remove(); callback.setReturnValue(drops);
    }
    @Redirect(method="popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
        at=@At(value="FIELD",target="Lnet/minecraft/world/level/block/Block;capturedDrops:Ljava/util/List;",opcode=178))
    private static List<ItemEntity> muxi$currentDrops() { return muxi$drops.get().peek(); }
}
