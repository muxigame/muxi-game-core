package net.muxigame.transferprobe.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public class ClientScreenMixin {
    @Shadow @Final private BooleanSupplier levelReceived;
    @Inject(method = "render", at = @At("HEAD"))
    private void probe$frame(CallbackInfo ci) { Trace.client("screen_first_frame"); }
    @Inject(method = "onClose", at = @At("HEAD"))
    private void probe$close(CallbackInfo ci) {
        Trace.event("client", Trace.client(), "screen_close_reason", true,
                "reason", levelReceived.getAsBoolean() ? "ready" : "timeout_or_external");
    }
}
