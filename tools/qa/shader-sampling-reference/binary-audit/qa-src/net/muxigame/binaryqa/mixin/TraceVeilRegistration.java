package net.muxigame.binaryqa.mixin;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="foundry.veil.forge.platform.NeoForgeVeilClientPlatform",remap=false)
public class TraceVeilRegistration {
 @Inject(method="onRegisterShaderPreProcessors",at=@At("HEAD")) private void qa$begin(CallbackInfo ci){Trace.begin("veilRegistration");}
 @Inject(method="onRegisterShaderPreProcessors",at=@At("RETURN")) private void qa$end(CallbackInfo ci){Trace.end("veilRegistration");}
}
