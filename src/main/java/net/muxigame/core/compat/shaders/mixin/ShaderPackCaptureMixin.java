package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import java.nio.file.Path;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.irisshaders.iris.shaderpack.ShaderPack",remap=false)
public class ShaderPackCaptureMixin {
    @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At("RETURN"),remap=false,require=0)
    private void muxi$capture(Path root,Map<String,String> configs,@Coerce Object defines,boolean zipped,CallbackInfo ci){DimensionShaderSwap.captured(root,configs,defines,zipped,this);}
}
