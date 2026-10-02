package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import net.muxigame.core.compat.shaders.ShaderPackSourceCarrier;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.irisshaders.iris.shaderpack.ShaderPack",remap=false)
public class ShaderPackCaptureMixin implements ShaderPackSourceCarrier {
    @Unique private ShaderPackSourceCarrier.Source muxi$source;
    @Override public ShaderPackSourceCarrier.Source muxi$shaderPackSource(){return muxi$source;}
    @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At("RETURN"),remap=false,require=0)
    private void muxi$capture(Path root,Map<String,String> configs,@Coerce Object defines,boolean zipped,CallbackInfo ci){
        Map<String,String> macros=Map.of();
        try{var values=new HashMap<String,String>();for(Object pair:(Iterable<?>)defines){var type=pair.getClass();values.put(String.valueOf(type.getMethod("key").invoke(pair)),String.valueOf(type.getMethod("value").invoke(pair)));}macros=Map.copyOf(values);}catch(ReflectiveOperationException|RuntimeException ignored){}
        muxi$source=new ShaderPackSourceCarrier.Source(root,configs==null?Map.of():Map.copyOf(configs),zipped,macros);
        DimensionShaderSwap.captured(root,configs,defines,zipped,this);
    }
}
