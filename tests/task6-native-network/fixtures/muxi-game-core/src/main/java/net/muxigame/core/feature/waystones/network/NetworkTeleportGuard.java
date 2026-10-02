package net.muxigame.core.feature.waystones.network;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.lang.ref.WeakReference;
import java.util.*;

/** Guards only network-owned contexts; ordinary Waystones item/physical travel is unchanged. */
public final class NetworkTeleportGuard {
    private NetworkTeleportGuard() {}
    private record Guard(ServerPlayer player,WeakReference<Object> connection,ResourceLocation sourceDimension,
                         NetworkPortalFacilities.Source source,BlockPos sourcePos,UUID target,
                         ResourceLocation targetDimension,BlockPos targetPos,int deadline) {}
    private static final Map<WaystoneTeleportContext,Guard> guards=Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean registered;
    public static synchronized void register() {
        if(registered)return;registered=true;
        Balm.getEvents().onEvent(WaystoneTeleportEntityEvent.Pre.class,event->{
            if(event.getEntity()!=event.getContext().getEntity())return;
            String reason=denial(event.getContext(),true);
            if(reason!=null)event.overrideResult(EntityTeleportResult.failed(event.getEntity(),event.getOriginalDestination(),new WaystoneTeleportError(Component.literal(reason))));
        });
    }
    public static void track(WaystoneTeleportContext context,ServerPlayer player,NetworkPortalFacilities.Source source,Waystone target) {
        guards.put(context,new Guard(player,new WeakReference<>(player.connection),player.serverLevel().dimension().location(),source,source.stone().getPos().immutable(),target.getWaystoneUid(),target.getDimension().location(),target.getPos().immutable(),player.tickCount+400));
    }
    public static void untrack(WaystoneTeleportContext context){guards.remove(context);}
    public static String initialDenial(ServerPlayer player,NetworkPortalFacilities.Source source,Waystone target,NetworkFacilityConfig cfg) {
        if(!player.isAlive() || player.hasDisconnected() || net.muxigame.minigames.GameRuntime.blocksWorldTravel(player))return "当前状态不能传送";
        if(source==null)return "请靠近真实石碑或已连接石碑的服务器设施门";
        if(target==null || !target.isValid())return "目标石碑无效";
        if(!WaystoneTypes.isSharestone(target.getWaystoneType()) && !WaystonesAPI.isWaystoneActivated(player,target))return "目标石碑尚未激活";
        if(!player.serverLevel().dimension().equals(target.getDimension()) && !NetworkPortalFacilities.currentDimensionGateway(player,cfg))return "当前维度的石碑网络尚未连接服务器设施门，不能跨维度传送";
        return null;
    }
    /** Native pending-validation hook executes before Waystones consumes its original requirements. */
    public static String denial(WaystoneTeleportContext context) {
        return denial(context,false);
    }
    public static String denial(WaystoneTeleportContext context,boolean prepared) {
        Guard guard=guards.get(context);if(guard==null)return null;
        ServerPlayer player=guard.player;
        if(player.connection!=guard.connection.get() || player.hasDisconnected() || player.server.getPlayerList().getPlayer(player.getUUID())!=player)return "连接已变化，已取消旧传送";
        if(player.tickCount>guard.deadline || !player.serverLevel().dimension().location().equals(guard.sourceDimension))return "入口环境已变化，请重新选择";
        var cfg=NetworkPortalFacilities.config();var current=NetworkPortalFacilities.source(player,cfg);
        if(current==null || !current.stone().getWaystoneUid().equals(guard.source.stone().getWaystoneUid())
            || !current.stone().getPos().equals(guard.sourcePos)
            || !Objects.equals(current.facility(),guard.source.facility()) || !current.frameKey().equals(guard.source.frameKey()))return "入口或门与石碑关联已变化，已取消传送";
        if(guard.source.facility()!=null && !cfg.facilities().contains(guard.source.facility()))return "服务器设施登记已变化，已取消传送";
        Waystone target=WaystonesAPI.getWaystone(player.server,guard.target).orElse(null);
        if(target==null || !target.getWaystoneUid().equals(context.getTargetWaystone().getWaystoneUid())
            || !target.getDimension().location().equals(guard.targetDimension) || !target.getPos().equals(guard.targetPos))return "目标石碑已变化";
        var targetLevel=player.server.getLevel(target.getDimension());
        if(targetLevel==null || ((prepared || targetLevel.hasChunkAt(target.getPos())) && !NetworkPortalFacilities.actualStone(targetLevel,target)))return "目标石碑已失效";
        return initialDenial(player,current,target,cfg);
    }
}
