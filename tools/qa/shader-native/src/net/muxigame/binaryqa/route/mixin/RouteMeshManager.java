package net.muxigame.binaryqa.route.mixin;
import net.muxigame.binaryqa.route.*;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=RenderSectionManager.class,remap=false)
public class RouteMeshManager {
 @Shadow @Final private ClientLevel level;
 @Unique private long routeTimelineUploadStart,routeTimelineUploadToken,routeTimelineScheduleStart,routeTimelineScheduleToken;
 @Unique private boolean routeTimelineMatch(){return RouteTimeline.token()!=0&&RouteTimeline.dimensionMatches(level.dimension().location().toString());}
 @Inject(method="createRebuildTask",at=@At("RETURN"),require=1)
 private void routeTask(RenderSection section,int frame,CallbackInfoReturnable<ChunkBuilderMeshingTask> ci){
   if(!routeTimelineMatch())return;long t=RouteTimeline.token();
   if(ci.getReturnValue()!=null){((TaskTag)(Object)ci.getReturnValue()).routeTimelineTag(t,section.getChunkX(),section.getChunkY(),section.getChunkZ());
   RouteTimeline.event(t,"meshTaskCreated",section.getChunkX(),section.getChunkY(),section.getChunkZ(),0);}
   else RouteTimeline.event(t,"meshTaskCreateNull",section.getChunkX(),section.getChunkY(),section.getChunkZ(),0);
 }
 @Inject(method="scheduleRebuild(IIIZ)V",at=@At("HEAD"),require=1)
 private void routeRebuild(int x,int y,int z,boolean important,CallbackInfo ci){if(routeTimelineMatch())RouteTimeline.event(RouteTimeline.token(),"meshRebuildRequested",x,y,z,0);}
 @Inject(method="updateChunks(Z)V",at=@At("HEAD"),require=1)
 private void routeScheduleHead(CallbackInfo ci){routeTimelineScheduleToken=routeTimelineMatch()?RouteTimeline.token():0;if(routeTimelineScheduleToken!=0)routeTimelineScheduleStart=System.nanoTime();}
 @Inject(method="updateChunks(Z)V",at=@At("RETURN"),require=1)
 private void routeScheduleReturn(CallbackInfo ci){RouteTimeline.event(routeTimelineScheduleToken,"meshScheduleBatchReturn",Integer.MIN_VALUE,0,0,System.nanoTime()-routeTimelineScheduleStart);}
 @Inject(method="uploadChunks()V",at=@At("HEAD"),require=1)
 private void routeUploadHead(CallbackInfo ci){routeTimelineUploadToken=routeTimelineMatch()?RouteTimeline.token():0;if(routeTimelineUploadToken!=0)routeTimelineUploadStart=System.nanoTime();}
 @Inject(method="uploadChunks()V",at=@At("RETURN"),require=1)
 private void routeUploadReturn(CallbackInfo ci){RouteTimeline.event(routeTimelineUploadToken,"meshUploadBatchReturn",Integer.MIN_VALUE,0,0,System.nanoTime()-routeTimelineUploadStart);}
}
