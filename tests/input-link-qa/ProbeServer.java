package net.muxigame.inputlinkqa;
import com.google.gson.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
@Mod(value="input_link_qa",dist=Dist.DEDICATED_SERVER)
public final class ProbeServer {
    public ProbeServer(){if(ProbeFiles.enabled())NeoForge.EVENT_BUS.addListener(this::tick);}
    void tick(ServerTickEvent.Post event){if(event.getServer().getTickCount()%5!=0)return;try{var r=new JsonObject();r.addProperty("outbreakInteractReceived",ProbeCounters.received.get());r.addProperty("tick",event.getServer().getTickCount());ProbeFiles.write("inputlink-server.json",r);}catch(Exception busy){}}
}
