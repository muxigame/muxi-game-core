package net.muxigame.schematicqa.mixin;
import net.muxigame.schematicqa.SchematicQA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Observe the real schematic renderer; never replace its draw or compile methods. */
@Mixin(targets="fi.dy.masa.litematica.render.schematic.WorldRendererSchematic",remap=false)
public abstract class RenderEvidenceMixin {
 @Inject(method="renderBlockLayer",at=@At("RETURN"),remap=false)
 private void task23$drawn(CallbackInfoReturnable<Integer> result){
  SchematicQA.layerCalls.incrementAndGet();int count=result.getReturnValue();if(count>0)SchematicQA.drawnChunks.addAndGet(count);
 }
 @Inject(method="renderBlock",at=@At("RETURN"),remap=false)
 private void task23$compiled(CallbackInfoReturnable<Boolean> result){if(Boolean.TRUE.equals(result.getReturnValue()))SchematicQA.compiledBlocks.incrementAndGet();}
}
