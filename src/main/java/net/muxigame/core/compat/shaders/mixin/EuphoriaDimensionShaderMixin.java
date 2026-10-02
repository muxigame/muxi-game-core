package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager",remap=false)
public class EuphoriaDimensionShaderMixin {
    @Inject(method="findAndScheduleReload",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void muxi$schedule(CallbackInfo ci){if(DimensionShaderSwap.consumeDimensionReload())ci.cancel();}
}
