package net.muxigame.core.client.challenge;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.muxigame.core.feature.challenge.ChallengeNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class ChallengeClient {
    private ChallengeClient(){}
    public static JsonObject state=new JsonObject();
    public static void register(IEventBus bus){ChallengeNetwork.receiver(s->{try{state=JsonParser.parseString(s).getAsJsonObject();}catch(RuntimeException ignored){}});bus.addListener(ChallengeClient::logout);}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new JsonObject();}
    public static boolean supported(){var c=Minecraft.getInstance().getConnection();return c!=null && NetworkRegistry.hasChannel(c,ChallengeNetwork.Action.TYPE.id());}
    public static void action(String action,String value){if(supported())PacketDistributor.sendToServer(new ChallengeNetwork.Action(action,value));}
    public static void open(){Minecraft.getInstance().setScreen(new ChallengeScreen());}
    public static String text(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
    public static int number(JsonObject o,String key){return o.has(key)?o.get(key).getAsInt():0;}
    public static boolean flag(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    public static JsonArray array(String key){return state.has(key)?state.getAsJsonArray(key):new JsonArray();}
    public static JsonObject mine(){for(JsonElement e:array("rooms"))if(flag(e.getAsJsonObject(),"mine"))return e.getAsJsonObject();return null;}
    public static String summary(){var r=mine();return r==null?"封锁研究所 · 单人 / 1–4 人合作":phase(text(r,"phase"))+" · "+number(r,"wave")+"/"+number(r,"total")+" 波 · 剩余 "+number(r,"remaining");}
    private static String phase(String s){return switch(s){case "BUILDING"->"地图生成";case "LOBBY"->"等待开局";case "COUNTDOWN"->"准备倒计时";case "REST"->"波间休整";case "RUNNING"->"战斗中";default->s;};}
}
