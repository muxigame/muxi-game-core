package net.muxigame.core.compat.shaders.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.GlStateManager;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GlStateManager.class)
public class ProgramBinaryGlMixin {
 @WrapMethod(method="glCreateProgram") private static int muxi$create(Operation<Integer> original){int id=original.call();if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.create(id);return id;}
 @WrapMethod(method="_glBindAttribLocation") private static void muxi$bind(int id,int index,CharSequence name,Operation<Void> original){original.call(id,index,name);if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.bind(id,index,name);}
 @WrapMethod(method="glDeleteProgram") private static void muxi$delete(int id,Operation<Void> original){if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.delete(id);original.call(id);}
 @WrapMethod(method="glLinkProgram") private static void muxi$link(int id,Operation<Void> original){if(!ShaderBinaryBootstrap.ready){original.call(id);return;}ShaderProgramBinary.link(id,()->original.call(id));}
}
