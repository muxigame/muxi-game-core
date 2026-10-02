package net.muxigame.core.compat.shaders.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.shaders.*;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ProgramManager.class)
public class ProgramBinaryScopeMixin {
 @WrapMethod(method="linkShader") private static void muxi$scope(Shader shader,Operation<Void> original){if(!ShaderBinaryBootstrap.ready){original.call(shader);return;}boolean old=ShaderProgramBinary.enter(shader);try{original.call(shader);}finally{ShaderProgramBinary.leave(old);}}
}
