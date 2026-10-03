package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GlStateManager.class)
public class TraceGlCompile {
 @WrapMethod(method="glCompileShader") private static void qa$compile(int id,Operation<Void> original){if(!Trace.ready){original.call(id);return;}String name=Trace.compileFamily();long begin=System.nanoTime();try{original.call(id);}finally{Trace.add(name,begin,System.nanoTime());}}
}
