package net.muxigame.core.compat.shaders.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.*;
@Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder",remap=false)
public class ProgramBinarySodiumMixin {
 @Unique private final Map<String,Integer> muxi$attributes=new TreeMap<>(),muxi$fragments=new TreeMap<>();
 @WrapOperation(method="bindAttribute",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glBindAttribLocation(IILjava/lang/CharSequence;)V"),require=0)
 private void muxi$attribute(int id,int index,CharSequence name,Operation<Void> original){original.call(id,index,name);if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.bind(muxi$attributes,index,name);}
 @WrapOperation(method="bindFragmentData",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL30C;glBindFragDataLocation(IILjava/lang/CharSequence;)V"),require=0)
 private void muxi$fragment(int id,int index,CharSequence name,Operation<Void> original){original.call(id,index,name);if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.bind(muxi$fragments,index,name);}
 @WrapOperation(method="link",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glLinkProgram(I)V"),require=0)
 private void muxi$link(int id,Operation<Void> original){if(!ShaderBinaryBootstrap.ready){original.call(id);return;}ShaderProgramBinary.link(id,muxi$attributes,muxi$fragments,"iris-sodium",()->original.call(id));}
}
