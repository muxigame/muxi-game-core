package net.muxigame.binaryqa.serverroute.mixin;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.muxigame.binaryqa.serverroute.ServerRouteScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public class ServerRouteChunks {
 @WrapMethod(method="getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
 private ChunkAccess routeChunk(int x,int z,ChunkStatus status,boolean create,Operation<ChunkAccess> original){
   long begin=ServerRouteScope.start();
   try{return original.call(x,z,status,create);}
   finally{ServerRouteScope.finish(create?"serverRouteGetChunkCreate":"serverRouteGetChunkNoCreate",begin,x,z);}
 }
 @WrapOperation(method="getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"),require=1)
 private void routeManagedBlock(@Coerce Object executor,BooleanSupplier done,Operation<Void> original){
   long begin=ServerRouteScope.start();
   try{original.call(executor,done);}
   finally{ServerRouteScope.finish("serverRouteChunkManagedBlock",begin);}
 }
}
