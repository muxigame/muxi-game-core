package net.muxigame.binaryqa.route.mixin;
import net.minecraft.server.network.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.muxigame.shadernative.ServerTimeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerChunkSender.class)
public class RouteServerSend {
 @Unique private static final ThreadLocal<Long> routeTimelineSendToken=ThreadLocal.withInitial(()->0L);
 @Inject(method="sendChunk",at=@At("HEAD"),require=1)
 private static void routeHead(ServerGamePacketListenerImpl listener,ServerLevel level,LevelChunk chunk,CallbackInfo ci){long t=ServerTimeline.dimensionMatches(level.dimension().location().toString())?ServerTimeline.token():0;routeTimelineSendToken.set(t);ServerTimeline.event(t,"serverSendChunkHead",chunk.getPos().x,Integer.MIN_VALUE,chunk.getPos().z,0);}
 @Inject(method="sendChunk",at=@At("RETURN"),require=1)
 private static void routeReturn(ServerGamePacketListenerImpl listener,ServerLevel level,LevelChunk chunk,CallbackInfo ci){ServerTimeline.event(routeTimelineSendToken.get(),"serverSendChunkReturn",chunk.getPos().x,Integer.MIN_VALUE,chunk.getPos().z,0);routeTimelineSendToken.remove();}
}
