package net.muxigame.core.feature.waystones;

import io.netty.handler.codec.DecoderException;
import net.blay09.mods.waystones.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.muxigame.core.feature.waystones.network.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;
import java.lang.ref.WeakReference;

/** No actor, permission, fee or arbitrary coordinate destination is accepted from a client. */
public final class WaystoneMapNetwork {
    private WaystoneMapNetwork() {}
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("muxi_game_core",name); }
    private static volatile Consumer<View> viewReceiver=ignored->{};
    private static volatile Consumer<Receipt> receiptReceiver=ignored->{};
    private static final Map<Object,State> states=new WeakHashMap<>();
    private static final class State {
        final WeakReference<ServerPlayer> actor;
        int lastView=Integer.MIN_VALUE, lastRequest=Integer.MIN_VALUE;
        UUID pending; final LinkedHashMap<UUID,Receipt> recent=new LinkedHashMap<>();
        State(ServerPlayer player){actor=new WeakReference<>(player);}
    }
    private static State state(ServerPlayer player) {
        State state=states.get(player.connection);
        if(state==null || state.actor.get()!=player){state=new State(player);states.put(player.connection,state);}
        return state;
    }
    public static void clientReceivers(Consumer<View> view,Consumer<Receipt> receipt) { viewReceiver=view;receiptReceiver=receipt; }
    /** Legacy packets still resolve a real server stone and run the same guard. */
    public record Teleport(ResourceLocation dimension,BlockPos pos) implements CustomPacketPayload {
        public static final Type<Teleport> TYPE=new Type<>(id("waystone_map_teleport_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Teleport> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeBlockPos(p.pos);},b->new Teleport(b.readResourceLocation(),b.readBlockPos()));
        public Type<Teleport> type(){return TYPE;}
    }
    public record ViewRequest(UUID request) implements CustomPacketPayload {
        public static final Type<ViewRequest> TYPE=new Type<>(id("waystone_network_view_request_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ViewRequest> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.request),b->new ViewRequest(b.readUUID()));
        public Type<ViewRequest> type(){return TYPE;}
    }
    public record Node(UUID uid,ResourceLocation dimension,BlockPos pos,boolean portalLinked) {}
    public record View(UUID request,ResourceLocation sourceDimension,boolean nearSource,boolean crossDimensionReady,List<Node> nodes) implements CustomPacketPayload {
        public View { nodes=List.copyOf(nodes); }
        public static final Type<View> TYPE=new Type<>(id("waystone_network_view_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.request);b.writeResourceLocation(p.sourceDimension);b.writeBoolean(p.nearSource);b.writeBoolean(p.crossDimensionReady);b.writeVarInt(p.nodes.size());
            for(Node n:p.nodes){b.writeUUID(n.uid);b.writeResourceLocation(n.dimension);b.writeBlockPos(n.pos);b.writeBoolean(n.portalLinked);}
        },b->{
            UUID request=b.readUUID();ResourceLocation dimension=b.readResourceLocation();boolean near=b.readBoolean(),cross=b.readBoolean();int count=b.readVarInt();
            if(count<0 || count>256)throw new DecoderException("Invalid network node count");
            List<Node> nodes=new ArrayList<>();for(int i=0;i<count;i++)nodes.add(new Node(b.readUUID(),b.readResourceLocation(),b.readBlockPos(),b.readBoolean()));
            return new View(request,dimension,near,cross,nodes);
        });
        public Type<View> type(){return TYPE;}
    }
    public record Warp(UUID request,UUID target) implements CustomPacketPayload {
        public static final Type<Warp> TYPE=new Type<>(id("waystone_network_warp_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Warp> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeUUID(p.target);},b->new Warp(b.readUUID(),b.readUUID()));
        public Type<Warp> type(){return TYPE;}
    }
    /** pending is deliberately distinct from corroborated arrival. */
    public record Receipt(UUID request,UUID target,String status,String message) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(id("waystone_network_receipt_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeUUID(p.target);b.writeUtf(p.status,16);b.writeUtf(p.message,256);},b->new Receipt(b.readUUID(),b.readUUID(),b.readUtf(16),b.readUtf(256)));
        public Type<Receipt> type(){return TYPE;}
    }
    public static void register(IEventBus modBus) {
        modBus.addListener(WaystoneMapNetwork::registerPayloads);
        NetworkTeleportGuard.register();
    }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var old=event.registrar("waystone-map-1").optional();
        old.playToServer(Teleport.TYPE,Teleport.CODEC,(p,c)->{
            if(c.player() instanceof ServerPlayer player) {
                ServerLevel level=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension));
                Waystone target=level==null?null:WaystonesAPI.getWaystoneAt(level,p.pos).orElse(null);
                execute(player,UUID.randomUUID(),target==null?new UUID(0,0):target.getWaystoneUid());
            }
        });
        var r=event.registrar("waystone-network-2").optional();
        r.playToServer(ViewRequest.TYPE,ViewRequest.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)view(player,p);});
        r.playToServer(Warp.TYPE,Warp.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)execute(player,p.request,p.target);});
        r.playToClient(View.TYPE,View.CODEC,(p,c)->viewReceiver.accept(p));
        r.playToClient(Receipt.TYPE,Receipt.CODEC,(p,c)->receiptReceiver.accept(p));
    }
    private static void view(ServerPlayer player,ViewRequest request) {
        State state=state(player);
        if(state.lastView!=Integer.MIN_VALUE && player.tickCount-state.lastView<20)return;
        state.lastView=player.tickCount;var cfg=NetworkPortalFacilities.config();
        List<Node> nodes=new ArrayList<>();
        WaystonesAPI.getAllWaystones(player.server).filter(Waystone::isValid)
            .filter(s->WaystoneTypes.isSharestone(s.getWaystoneType()) || WaystonesAPI.isWaystoneActivated(player,s))
            .limit(256).forEach(s->nodes.add(new Node(s.getWaystoneUid(),s.getDimension().location(),s.getPos(),NetworkPortalFacilities.portalBadge(player.server,s,cfg))));
        if(NetworkRegistry.hasChannel(player.connection,View.TYPE.id()))PacketDistributor.sendToPlayer(player,new View(request.request,player.serverLevel().dimension().location(),NetworkPortalFacilities.source(player,cfg)!=null,NetworkPortalFacilities.currentDimensionGateway(player,cfg),nodes));
    }
    private static void send(ServerPlayer player,Receipt receipt) {
        if(!player.hasDisconnected() && NetworkRegistry.hasChannel(player.connection,Receipt.TYPE.id()))PacketDistributor.sendToPlayer(player,receipt);
        if(!receipt.status.equals("pending"))player.displayClientMessage(Component.literal(receipt.message),true);
    }
    private static void finish(ServerPlayer player,State state,UUID request,UUID target,String status,String message) {
        if(message.length()>256)message=message.substring(0,255)+"…";
        Receipt receipt=new Receipt(request,target,status,message);state.recent.put(request,receipt);
        while(state.recent.size()>32)state.recent.remove(state.recent.keySet().iterator().next());
        if(request.equals(state.pending))state.pending=null;
        send(player,receipt);
    }
    private static void execute(ServerPlayer player,UUID request,UUID targetUid) {
        State state=state(player);
        Receipt replay=state.recent.get(request);
        if(replay!=null){if(replay.target.equals(targetUid))send(player,replay);return;}
        if(state.pending!=null){send(player,new Receipt(request,targetUid,"failed","已有传送正在处理，请等待结果"));return;}
        if(state.lastRequest!=Integer.MIN_VALUE && player.tickCount-state.lastRequest<10){send(player,new Receipt(request,targetUid,"failed","请求过于频繁，请稍后重试"));return;}
        state.lastRequest=player.tickCount;
        Waystone target=WaystonesAPI.getWaystone(player.server,targetUid).orElse(null);
        var cfg=NetworkPortalFacilities.config();var source=NetworkPortalFacilities.source(player,cfg);
        String denied=NetworkTeleportGuard.initialDenial(player,source,target,cfg);
        if(denied!=null){finish(player,state,request,targetUid,"failed",denied);return;}
        var connection=player.connection;state.pending=request;
        WaystonesAPI.createDefaultTeleportContext(player,target,ctx->ctx.setFromWaystone(source.stone()))
            .ifRight(error->finish(player,state,request,targetUid,"failed",error.getComponent().getString()))
            .ifLeft(ctx->{
                NetworkTeleportGuard.track(ctx,player,source,target);
                send(player,new Receipt(request,targetUid,"pending","正在按石碑原有规则准备传送"));
                try {
                    WaystonesAPI.tryTeleportAsync(ctx).whenComplete((result,error)->player.server.execute(()->{
                        NetworkTeleportGuard.untrack(ctx);
                        if(player.connection!=connection || player.hasDisconnected() || player.server.getPlayerList().getPlayer(player.getUUID())!=player){state.pending=null;return;}
                        if(error!=null || result==null){finish(player,state,request,targetUid,"failed","传送准备失败，请稍后重试");return;}
                        result.ifRight(failure->finish(player,state,request,targetUid,"failed",failure.getComponent().getString()));
                        result.ifLeft(arrived->{
                            boolean actual=arrived.stream().anyMatch(e->e==player) && player.serverLevel().dimension().equals(target.getDimension())
                                && player.blockPosition().distSqr(target.getPos())<=256;
                            finish(player,state,request,targetUid,actual?"completed":"failed",actual?"已到达石碑":"未确认实际到达，请检查传送结果");
                        });
                    }));
                } catch(RuntimeException error){NetworkTeleportGuard.untrack(ctx);finish(player,state,request,targetUid,"failed","传送准备失败，请稍后重试");}
            });
    }
}
