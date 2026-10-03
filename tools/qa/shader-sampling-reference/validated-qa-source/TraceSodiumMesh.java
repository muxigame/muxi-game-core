package net.muxigame.binaryqa.mixin;
import net.muxigame.binaryqa.Trace;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",remap=false) public class TraceSodiumMesh {
 @Inject(method="update",at=@At("HEAD")) private void update(CallbackInfo ci){Trace.begin("sodiumGraphUpdate");}
 @Inject(method="update",at=@At("RETURN")) private void updateEnd(CallbackInfo ci){Trace.end("sodiumGraphUpdate");}
 @Inject(method="uploadChunks",at=@At("HEAD")) private void upload(CallbackInfo ci){Trace.begin("sodiumMeshUpload");}
 @Inject(method="uploadChunks",at=@At("RETURN")) private void uploadEnd(CallbackInfo ci){Trace.end("sodiumMeshUpload");}
 @Inject(method="updateChunks",at=@At("HEAD"),require=0) private void build(CallbackInfo ci){Trace.begin("sodiumMeshSchedule");}
 @Inject(method="updateChunks",at=@At("RETURN"),require=0) private void buildEnd(CallbackInfo ci){Trace.end("sodiumMeshSchedule");}
}
