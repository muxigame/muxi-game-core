package net.muxigame.core.taskssmoke;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.phys.Vec3;
import net.muxigame.core.threading.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

@Mod("muxi_tasks_smoke")
public final class TravelSmoke {
    private final List<String> passed=new ArrayList<>();
    private ServerPlayer player;
    private int tick,stage,corrections,vehicleCorrections,stageTick;
    private boolean done;
    private long coldNanos;
    public TravelSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::stopped);}
    private void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        try{Files.writeString(Path.of("dimensions-server-stopped"),"stopped");}catch(Exception error){throw new RuntimeException(error);}
    }
    private void check(String name,boolean condition){if(!condition)throw new AssertionError(name);passed.add(name);}
    @SuppressWarnings("unchecked")
    private void tick(ServerTickEvent.Post event) {
        if(done)return;tick++;stageTick++;
        var server=event.getServer();
        try {
            if(tick>1600)throw new AssertionError("travel smoke timeout stage "+stage);
            if(stage==0) {
                check("travel enabled",ChunkTravel.ENABLED);
                var profile=new GameProfile(UUID.randomUUID(),"TravelProbe");
                player=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
                var transport=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(transport);
                player.connection=new ServerGamePacketListenerImpl(server,transport,player,CommonListenerCookie.createInitial(profile,false)) {
                    @Override public void send(Packet<?> packet){if(packet instanceof ClientboundPlayerPositionPacket)corrections++;}
                };
                player.setPos(4096.5,200,4096.5);
                check("cold chunk initially absent",server.overworld().getChunkSource().getChunkNow(256,256)==null);
                long start=System.nanoTime();
                player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(4096.7,200,4096.5,false));
                coldNanos=System.nanoTime()-start;
                check("cold packet holds position",player.getX()==4096.5);
                check("cold packet sends correction",corrections==1);
                check("cold packet does not synchronously generate",server.overworld().getChunkSource().getChunkNow(256,256)==null);
                check("cold admission under 250 ms",coldNanos<250_000_000);
                check("oversized packet bounded",!ChunkTravel.allow(player,player,500000,200,500000));
                var riderProfile=new GameProfile(UUID.randomUUID(),"VehicleProbe");
                var rider=new ServerPlayer(server,server.overworld(),riderProfile,ClientInformation.createDefault());
                var vehicleTransport=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(vehicleTransport);
                int[] ridingTeleport={-1};
                rider.connection=new ServerGamePacketListenerImpl(server,vehicleTransport,rider,CommonListenerCookie.createInitial(riderProfile,false)) {
                    @Override public void send(Packet<?> packet){
                        if(packet instanceof ClientboundMoveVehiclePacket)vehicleCorrections++;
                        if(packet instanceof ClientboundPlayerPositionPacket position)ridingTeleport[0]=position.getId();
                    }
                };
                var boat=new net.minecraft.world.entity.vehicle.Boat(net.minecraft.world.entity.EntityType.BOAT,server.overworld());
                boat.setPos(8192.5,200,8192.5);rider.setPos(8192.5,200,8192.5);
                check("rider mounted",rider.startRiding(boat,true));
                check("mount teleport sent",ridingTeleport[0]>=0);
                rider.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(ridingTeleport[0]));
                var lastVehicle=ServerGamePacketListenerImpl.class.getDeclaredField("lastVehicle");lastVehicle.setAccessible(true);lastVehicle.set(rider.connection,boat);
                boat.setPos(8192.7,200,8192.5);var vehiclePacket=new ServerboundMoveVehiclePacket(boat);boat.setPos(8192.5,200,8192.5);
                rider.connection.handleMoveVehicle(vehiclePacket);
                check("cold vehicle packet holds position",boat.getX()==8192.5);
                check("cold vehicle packet sends correction",vehicleCorrections==1);
                check("vehicle path does not synchronously generate",server.overworld().getChunkSource().getChunkNow(512,512)==null);
                rider.stopRiding();
                stage=1;stageTick=0;
            } else if(stage==1 && ChunkTravel.allow(player,player,4096.7,200,4096.5)) {
                check("held path eventually ready",true);
                // A turn must check the new swept footprint, not reuse the previous direction.
                check("sharp turn into cold terrain held",!ChunkTravel.allow(player,player,4096.5,200,4144.5));
                stage=2;stageTick=0;
            } else if(stage==2 && ChunkTravel.allow(player,player,4096.5,200,4144.5)) {
                check("turned path eventually ready",true);
                var source=server.createCommandSourceStack().withLevel(server.overworld()).withPosition(new Vec3(-4096,200,-4096));
                check("pregen command accepted",server.getCommands().getDispatcher().execute("muxichunks pregen 1",source)==1);
                stage=3;stageTick=0;
            } else if(stage==3) {
                var jobs=(Map<String,Object>)ChunkTravel.metrics(server).get("pregeneration");
                var job=(Map<String,Object>)jobs.get("minecraft:overworld");
                if(((Number)job.get("completed")).intValue()==9) {
                    check("nine pregen chunks complete",true);
                    var source=server.createCommandSourceStack().withLevel(server.overworld()).withPosition(new Vec3(-8192,200,-8192));
                    server.getCommands().getDispatcher().execute("muxichunks pregen 8",source);
                    server.getCommands().getDispatcher().execute("muxichunks cancel",source);
                    stage=4;stageTick=0;
                }
            } else if(stage==4 && stageTick>140) {
                var metrics=ChunkTravel.metrics(server);
                check("cancel removes pregen job",((Map<?,?>)metrics.get("pregeneration")).isEmpty());
                check("all temporary tickets released",((Number)metrics.get("tickets")).intValue()==0);
                check("ticket cap respected",((Number)metrics.get("maxTickets")).intValue()<=48);
                check("request cap respected",((Number)metrics.get("maxRequests")).intValue()<=256);
                finish(server,null);
            }
        }catch(Throwable error){finish(server,error);}
    }
    private void finish(net.minecraft.server.MinecraftServer server,Throwable error) {
        done=true;var result=new LinkedHashMap<String,Object>();result.put("success",error==null);result.put("passed",passed);
        result.put("coldAdmissionMillis",coldNanos/1e6);result.put("travel",ChunkTravel.metrics(server));
        if(error!=null){error.printStackTrace();result.put("error",error.toString());}
        try{Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}
        server.halt(false);
    }
}
