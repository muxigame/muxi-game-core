package net.muxigame.transferprobe.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
public class ClientLevelMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void probe$levelBegin(ClientLevel level, ReceivingLevelScreen.Reason reason, CallbackInfo ci) { Trace.client("set_level_begin"); }
    @Inject(method = "setLevel", at = @At("RETURN"))
    private void probe$levelEnd(ClientLevel level, ReceivingLevelScreen.Reason reason, CallbackInfo ci) { Trace.client("set_level_return"); }
    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void probe$enginesBegin(ClientLevel level, CallbackInfo ci) { Trace.client("engines_begin"); }
    @Inject(method = "updateLevelInEngines", at = @At("RETURN"))
    private void probe$enginesEnd(ClientLevel level, CallbackInfo ci) { Trace.client("engines_return"); }
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void probe$screen(Screen next, CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        if (next instanceof ReceivingLevelScreen) Trace.client("screen_open");
        else if (mc.screen instanceof ReceivingLevelScreen) {
            Trace.event("client", Trace.client(), "screen_close", true, "next_screen", next == null ? "none" : next.getClass().getName());
        }
    }
}
