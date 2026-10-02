package net.muxigame.core.client.waystones;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.waystones.WaystoneMapNetwork;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** A short-lived server snapshot annotates native markers; it never grants server authority. */
public final class WaystoneMapClient {
    private WaystoneMapClient() {}
    private static Object connection;
    private static Object playerIdentity;
    private static ResourceLocation dimension;
    private static UUID query,pending,pendingTarget;
    private static int requestedAt=-100,receivedAt=-100;
    private static WaystoneMapNetwork.View view;
    static { WaystoneMapNetwork.clientReceivers(WaystoneMapClient::receive,WaystoneMapClient::receive); }
    public static boolean isWaystoneOrigin(ResourceLocation origin){return origin!=null && "waystones".equals(origin.getNamespace());}
    private static boolean current() {
        Minecraft mc=Minecraft.getInstance();
        Object actual=mc.getConnection();ResourceLocation dim=mc.level==null?null:mc.level.dimension().location();
        if(actual!=connection || mc.player!=playerIdentity || !Objects.equals(dim,dimension)) {
            connection=actual;playerIdentity=mc.player;dimension=dim;query=null;pending=null;pendingTarget=null;view=null;requestedAt=-100;receivedAt=-100;
        }
        return actual!=null && mc.player!=null && dim!=null;
    }
    public static boolean supported() {
        return current() && NetworkRegistry.hasChannel(Minecraft.getInstance().getConnection(),WaystoneMapNetwork.Warp.TYPE.id())
            && NetworkRegistry.hasChannel(Minecraft.getInstance().getConnection(),WaystoneMapNetwork.View.TYPE.id());
    }
    /** Called only during native GuiMap rendering. No map key registration or automatic teleport. */
    public static void poll() {
        if(!supported())return;int tick=Minecraft.getInstance().player.tickCount;
        if(tick-requestedAt<20)return;
        requestedAt=tick;query=UUID.randomUUID();PacketDistributor.sendToServer(new WaystoneMapNetwork.ViewRequest(query));
    }
    private static void receive(WaystoneMapNetwork.View packet) {
        if(!current() || !packet.request().equals(query) || !packet.sourceDimension().equals(dimension))return;
        view=packet;receivedAt=Minecraft.getInstance().player.tickCount;
    }
    private static void receive(WaystoneMapNetwork.Receipt packet) {
        if(!current() || !packet.request().equals(pending) || !packet.target().equals(pendingTarget))return;
        if(!packet.status().equals("pending")){pending=null;pendingTarget=null;}
        if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.literal(packet.message()),true);
    }
    private static WaystoneMapNetwork.View view() {
        if(!current() || view==null)return null;
        int age=Minecraft.getInstance().player.tickCount-receivedAt;
        return age>=0 && age<=60?view:null;
    }
    public static boolean nearSource(){var s=view();return s!=null && s.nearSource();}
    public static WaystoneMapNetwork.Node node(ResourceKey<Level> dim,BlockPos pos) {
        var s=view();if(s==null || dim==null)return null;
        return s.nodes().stream().filter(n->n.dimension().equals(dim.location()) && n.pos().equals(pos)).findFirst().orElse(null);
    }
    public static boolean portalLinked(ResourceKey<Level> dim,BlockPos pos){var node=node(dim,pos);return node!=null && node.portalLinked();}
    public static String unavailableKey(ResourceKey<Level> targetDimension,BlockPos pos) {
        if(!supported())return "muxi.map.waystone.server_not_ready";
        var s=view();if(s==null || node(targetDimension,pos)==null)return "muxi.map.waystone.network_loading";
        if(pending!=null)return "muxi.map.waystone.network_pending";
        if(!s.nearSource())return "muxi.map.waystone.teleport_requires_source";
        if(!s.sourceDimension().equals(targetDimension.location()) && !s.crossDimensionReady())return "muxi.map.waystone.cross_dimension_not_connected";
        return null;
    }
    public static void teleport(ResourceKey<Level> dim,BlockPos pos) {
        String reason=unavailableKey(dim,pos);Minecraft mc=Minecraft.getInstance();
        if(reason!=null){if(mc.player!=null)mc.player.displayClientMessage(Component.translatable(reason),true);poll();return;}
        var target=node(dim,pos);pending=UUID.randomUUID();pendingTarget=target.uid();
        PacketDistributor.sendToServer(new WaystoneMapNetwork.Warp(pending,pendingTarget));
    }
}
