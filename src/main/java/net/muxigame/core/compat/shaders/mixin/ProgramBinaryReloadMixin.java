package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public class ProgramBinaryReloadMixin {
 @Inject(method="reload",at=@At("HEAD"),require=0) private static void muxi$reload(CallbackInfo ci){if(ShaderBinaryBootstrap.ready)ShaderProgramBinary.irisReload();}
}
