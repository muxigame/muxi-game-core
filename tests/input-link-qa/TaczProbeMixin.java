package net.muxigame.inputlinkqa.mixin;
import net.muxigame.inputlinkqa.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(com.tacz.guns.client.input.InteractKey.class)
public abstract class TaczProbeMixin {
    @Inject(method="doInteractLogic",at=@At("HEAD"))
    private static void probe(CallbackInfo ci){if(ProbeFiles.enabled())ProbeCounters.gun.incrementAndGet();}
}
