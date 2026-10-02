package net.muxigame.core.compat.shaders.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.*;
@Pseudo @Mixin(targets="dev.djefrey.colorwheel.compile.ClrwlProgram",remap=false)
public class ProgramBinaryColorwheelMixin {
 @Unique private final Map<String,Integer> muxi$attributes=new TreeMap<>();
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20;glBindAttribLocation(IILjava/lang/CharSequence;)V"),require=0)
 private void muxi$attribute(int id,int index,CharSequence name,Operation<Void> original){original.call(id,index,name);if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.bind(muxi$attributes,index,name);}
 @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20;glLinkProgram(I)V"),require=0)
 private void muxi$link(int id,Operation<Void> original){if(!ShaderBinaryBootstrap.ready){original.call(id);return;}ShaderProgramBinary.link(id,muxi$attributes,Map.of(),"iris-colorwheel",()->original.call(id));}
}
