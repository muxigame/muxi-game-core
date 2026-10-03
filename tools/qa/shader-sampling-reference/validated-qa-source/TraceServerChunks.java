package net.muxigame.binaryqa.mixin;
import net.minecraft.server.level.ServerChunkCache;import net.muxigame.binaryqa.Trace;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerChunkCache.class) public class TraceServerChunks {
 @Inject(method="getChunk",at=@At("HEAD")) private void begin(CallbackInfoReturnable<?> ci){Trace.begin("serverGetChunk");}
 @Inject(method="getChunk",at=@At("RETURN")) private void end(CallbackInfoReturnable<?> ci){Trace.end("serverGetChunk");}
 @Inject(method="tick",at=@At("HEAD")) private void tickBegin(CallbackInfo ci){Trace.begin("serverChunkTick");}
 @Inject(method="tick",at=@At("RETURN")) private void tickEnd(CallbackInfo ci){Trace.end("serverChunkTick");}
}
