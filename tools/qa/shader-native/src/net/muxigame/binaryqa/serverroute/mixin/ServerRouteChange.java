package net.muxigame.binaryqa.serverroute.mixin;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.resources.ResourceKey;
import net.muxigame.binaryqa.Trace;
import net.muxigame.shadernative.ServerTimeline;
import net.muxigame.binaryqa.serverroute.ServerRouteScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ServerPlayer.class)
public class ServerRouteChange {
 @WrapMethod(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;")
 private Entity routeChange(DimensionTransition transition,Operation<Entity> original){
  var player=(ServerPlayer)(Object)this;
  long token=ServerTimeline.token();
  if(!Trace.ready||token==0||!player.server.isSameThread())return original.call(transition);
  Long previous=ServerRouteScope.enter(token);long begin=ServerRouteScope.start();
  try{return original.call(transition);}
  finally{try{ServerRouteScope.finish("serverRouteChangeDimension",begin);}finally{ServerRouteScope.leave(previous);}}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/common/CommonHooks;onTravelToDimension(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceKey;)Z"),require=1)
 private boolean routeTravelEvent(Entity entity,ResourceKey<Level> target,Operation<Boolean> original){
  long begin=ServerRouteScope.start();try{return original.call(entity,target);}finally{ServerRouteScope.finish("serverRouteTravelEvent",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"),require=1)
 private void routeRemovePlayer(ServerLevel level,ServerPlayer player,Entity.RemovalReason reason,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(level,player,reason);}finally{ServerRouteScope.finish("serverRouteRemovePlayer",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"),require=1)
 private void routeTeleport(ServerGamePacketListenerImpl connection,double x,double y,double z,float yaw,float pitch,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(connection,x,y,z,yaw,pitch);}finally{ServerRouteScope.finish("serverRouteTeleport",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V"),require=1)
 private void routeAddDuringTeleport(ServerLevel level,Entity entity,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(level,entity);}finally{ServerRouteScope.finish("serverRouteAddDuringTeleport",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;sendLevelInfo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)V"),require=1)
 private void routeSendLevelInfo(PlayerList list,ServerPlayer player,ServerLevel level,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(list,player,level);}finally{ServerRouteScope.finish("serverRouteSendLevelInfo",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;sendAllPlayerInfo(Lnet/minecraft/server/level/ServerPlayer;)V"),require=1)
 private void routeSendAllPlayerInfo(PlayerList list,ServerPlayer player,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(list,player);}finally{ServerRouteScope.finish("serverRouteSendAllPlayerInfo",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/attachment/AttachmentSync;syncInitialPlayerAttachments(Lnet/minecraft/server/level/ServerPlayer;)V"),require=1)
 private void routeAttachmentSync(ServerPlayer player,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(player);}finally{ServerRouteScope.finish("serverRouteAttachmentSync",begin);}
 }
 @WrapOperation(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/event/EventHooks;firePlayerChangedDimensionEvent(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V"),require=1)
 private void routeChangedEvent(Player player,ResourceKey<Level> from,ResourceKey<Level> to,Operation<Void> original){
  long begin=ServerRouteScope.start();try{original.call(player,from,to);}finally{ServerRouteScope.finish("serverRouteChangedEvent",begin);}
 }
}
