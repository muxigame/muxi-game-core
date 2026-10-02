package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public class IrisDimensionShaderMixin {
    @Inject(method="reload",at=@At("HEAD"),remap=false,require=0)
    private static void muxi$reload(CallbackInfo ci){DimensionShaderSwap.genuineReload();}
}
