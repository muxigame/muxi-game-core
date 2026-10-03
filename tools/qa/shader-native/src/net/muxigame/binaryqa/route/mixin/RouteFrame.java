package net.muxigame.binaryqa.route.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.muxigame.binaryqa.route.RouteTimeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class RouteFrame {
 @Inject(method="renderLevel(Lnet/minecraft/client/DeltaTracker;)V",at=@At("RETURN"),require=1)
 private void routeFrame(CallbackInfo ci){RouteTimeline.frame();}
}
