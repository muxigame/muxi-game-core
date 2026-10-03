package net.muxigame.binaryqa.mixin;
import net.muxigame.binaryqa.Trace;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory",remap=false) public class TraceJeiItems {
 @Inject(method="create",at=@At("HEAD")) private static void begin(CallbackInfoReturnable<?> ci){Trace.begin("jeiItemStackCollection");}
 @Inject(method="create",at=@At("RETURN")) private static void end(CallbackInfoReturnable<?> ci){Trace.end("jeiItemStackCollection");}
}
