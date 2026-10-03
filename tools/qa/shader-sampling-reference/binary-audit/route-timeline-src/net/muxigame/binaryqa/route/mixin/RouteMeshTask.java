package net.muxigame.binaryqa.route.mixin;
import net.muxigame.binaryqa.route.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ChunkBuilderMeshingTask.class,remap=false)
public class RouteMeshTask implements TaskTag {
 @Unique private long routeTimelineGeneration,routeTimelineStart;
 @Unique private int routeTimelineX,routeTimelineY,routeTimelineZ;
 @Override public void routeTimelineTag(long route,int x,int y,int z){routeTimelineGeneration=route;routeTimelineX=x;routeTimelineY=y;routeTimelineZ=z;}
 @Inject(method="execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",at=@At("HEAD"),require=1)
 private void routeStart(CallbackInfoReturnable<?> ci){if(routeTimelineGeneration!=0){routeTimelineStart=System.nanoTime();RouteTimeline.event(routeTimelineGeneration,"meshBuildHead",routeTimelineX,routeTimelineY,routeTimelineZ,0);}}
 @Inject(method="execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",at=@At("RETURN"),require=1)
 private void routeEnd(CallbackInfoReturnable<?> ci){if(routeTimelineGeneration!=0)RouteTimeline.event(routeTimelineGeneration,ci.getReturnValue()==null?"meshBuildCancelledReturn":"meshBuildReturn",routeTimelineX,routeTimelineY,routeTimelineZ,System.nanoTime()-routeTimelineStart);}
}
