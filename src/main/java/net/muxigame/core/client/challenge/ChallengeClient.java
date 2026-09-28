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
    private static int ticks;
    public static void register(IEventBus bus){ChallengeNetwork.receiver(s->{try{state=JsonParser.parseString(s).getAsJsonObject();}catch(RuntimeException ignored){}});bus.addListener(ChallengeClient::logout);bus.addListener(ChallengeClient::tick);}
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(++ticks%20!=0||mc.player==null||mc.level==null)return;
        var r=mine();if(r!=null&&text(r,"phase").equals("LOADING")&&mc.level.dimension().equals(net.muxigame.core.feature.challenge.ChallengeArena.DIMENSION)&&mc.level.hasChunkAt(mc.player.blockPosition())&&!(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen))action("ready","");
    }
    public static boolean refillNearby(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||!supported()||!flag(state,"locked")||number(state,"arenaVersion")!=2||number(state,"ammoCooldown")>0)return false;
        if(!mc.level.dimension().equals(net.muxigame.core.feature.challenge.ChallengeArena.DIMENSION))return false;
        int floor=Math.max(0,Math.min(2,(int)Math.floor((mc.player.getY()-65)/10)));
        var station=new net.minecraft.core.BlockPos(number(state,"arenaOrigin")+40,65+floor*10,36);
        if(mc.player.distanceToSqr(station.getCenter())>25)return false;
        action("resupply","");return true;
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new JsonObject();}
    public static boolean supported(){var c=Minecraft.getInstance().getConnection();return c!=null && NetworkRegistry.hasChannel(c,ChallengeNetwork.Action.TYPE.id());}
    public static void action(String action,String value){if(supported())PacketDistributor.sendToServer(new ChallengeNetwork.Action(action,value));}
    public static void open(){Minecraft.getInstance().setScreen(new ChallengeScreen());}
    /** The player inventory already synchronizes the real gun model and its item components. */
    public static net.minecraft.world.item.ItemStack weaponIcon(int slot){
        var p=Minecraft.getInstance().player;
        return p!=null && slot>=0 && slot<36?p.getInventory().getItem(slot):net.minecraft.world.item.ItemStack.EMPTY;
    }
    public static String text(JsonObject o,String key){return o.has(key)?o.get(key).getAsString():"";}
    public static int number(JsonObject o,String key){return o.has(key)?o.get(key).getAsInt():0;}
    public static boolean flag(JsonObject o,String key){return o.has(key)&&o.get(key).getAsBoolean();}
    public static JsonArray array(String key){return state.has(key)?state.getAsJsonArray(key):new JsonArray();}
    public static JsonObject mine(){for(JsonElement e:array("rooms"))if(flag(e.getAsJsonObject(),"mine"))return e.getAsJsonObject();return null;}
    public static String activeSites(){var room=mine();if(room==null||!room.has("sites"))return "注意爆闪红灯，提前布防";return "激活："+java.util.stream.StreamSupport.stream(room.getAsJsonArray("sites").spliterator(),false).map(JsonElement::getAsString).collect(java.util.stream.Collectors.joining("、"));}
    public static String summary(){var r=mine();if(r==null)return "封锁研究所 · 单人 / 1–4 人合作";String s=text(r,"phase");if(s.equals("LOADING"))return "等待队员加载地图…";if(s.equals("COUNTDOWN")||s.equals("REST"))return (s.equals("COUNTDOWN")?"首波准备":"下一波")+" · "+number(r,"seconds")+" 秒";return phase(s)+" · "+number(r,"wave")+"/"+number(r,"total")+" 波 · 剩余 "+number(r,"remaining");}
    private static String phase(String s){return switch(s){case "BUILDING"->"地图生成";case "LOBBY"->"等待开局";case "LOADING"->"加载地图";case "COUNTDOWN"->"准备倒计时";case "REST"->"波间休整";case "RUNNING"->"战斗中";default->s;};}
}
