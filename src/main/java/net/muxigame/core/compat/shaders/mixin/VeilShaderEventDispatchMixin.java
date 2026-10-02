package net.muxigame.core.compat.shaders.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.core.compat.shaders.*;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo @Mixin(targets="foundry.veil.forge.platform.NeoForgeVeilClientPlatform",remap=false)
public class VeilShaderEventDispatchMixin {
 @WrapOperation(method="onRegisterShaderPreProcessors",at=@At(value="INVOKE",target="Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"),require=0)
 private void muxi$dispatch(Event event,Operation<Void> original){if(!ShaderBinaryBootstrap.ready){original.call(event);return;}VeilShaderEventDispatch.dispatch(event,()->original.call(event));}
}
