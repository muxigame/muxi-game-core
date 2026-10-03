package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.GlShader",remap=false)
public class TraceSodiumCompile {
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glCompileShader(I)V")) private void qa$compile(int id,Operation<Void> original){long begin=System.nanoTime();try{original.call(id);}finally{if(Trace.ready)Trace.add("compile-sodium",begin,System.nanoTime());}}
}
