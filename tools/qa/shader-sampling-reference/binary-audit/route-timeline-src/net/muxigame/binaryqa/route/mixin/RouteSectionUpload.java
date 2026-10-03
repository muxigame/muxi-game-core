package net.muxigame.binaryqa.route.mixin;
import net.muxigame.binaryqa.route.RouteTimeline;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RenderSection.class,remap=false)
public class RouteSectionUpload {
 @Inject(method="setLastUploadFrame(I)V",at=@At("RETURN"),require=1)
 private void routeUploaded(int frame,CallbackInfo ci){if(RouteTimeline.clientDimensionMatches()){var s=(RenderSection)(Object)this;RouteTimeline.event(RouteTimeline.token(),"sectionLastUploadFrameSet",s.getChunkX(),s.getChunkY(),s.getChunkZ(),0);}}
}
