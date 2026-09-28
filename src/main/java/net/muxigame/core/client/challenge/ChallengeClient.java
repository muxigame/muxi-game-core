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
    private static boolean holding;
    private static int holdTicks;
    private static final net.minecraft.client.KeyMapping WHEEL=new net.minecraft.client.KeyMapping("key.muxi_game_core.challenge_wheel",com.mojang.blaze3d.platform.InputConstants.KEY_B,"key.categories.muxi_game_core");
    public static void register(IEventBus modBus,IEventBus bus){ChallengeNetwork.receiver(s->{try{state=JsonParser.parseString(s).getAsJsonObject();if(!holding){state.addProperty("ammoHolding",false);state.addProperty("ammoProgress",0);}}catch(RuntimeException ignored){}});bus.addListener(ChallengeClient::logout);bus.addListener(ChallengeClient::tick);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->e.register(WHEEL));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e)->e.registerAboveAll(net.minecraft.resources.ResourceLocation.parse("muxi_game_core:challenge_combat"),(g,delta)->{var mc=Minecraft.getInstance();if(mc.player!=null&&mc.screen==null&&!mc.options.hideGui&&flag(state,"locked"))renderCombat(g);}));
    }
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();ticks++;if(mc.player==null||mc.level==null){holding=false;return;}
        while(WHEEL.consumeClick())if(mc.screen==null&&flag(state,"locked")){cancelRefill();mc.setScreen(new ChallengeWheelScreen());}
        if(holding){if(mc.screen!=null||!mc.isWindowActive()||!com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.isDown()||!nearSupply()||number(state,"ammoCooldown")>0)cancelRefill();else{holdTicks++;if(ticks%5==0){action("ammoHold","");if(holdTicks>=60)action("ammoFinish","");}}}
        if(ticks%20!=0)return;
        var r=mine();if(r!=null&&text(r,"phase").equals("LOADING")&&mc.level.dimension().equals(net.muxigame.core.feature.challenge.ChallengeArena.DIMENSION)&&mc.level.hasChunkAt(mc.player.blockPosition())&&!(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen))action("ready","");
    }
    public static boolean refillNearby(){
        var mc=Minecraft.getInstance();if(mc.screen!=null||!supported()||number(state,"ammoCooldown")>0||!nearSupply())return false;
        holding=true;holdTicks=0;action("ammoHold","");return true;
    }
    private static boolean nearSupply(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&flag(state,"locked")&&number(state,"arenaVersion")==4&&mc.level.dimension().equals(net.muxigame.core.feature.challenge.ChallengeArena.DIMENSION)&&mc.player.distanceToSqr(new net.minecraft.core.BlockPos(number(state,"arenaOrigin")+40,65,36).getCenter())<=25;}
    public static void cancelRefill(){if(holding)action("ammoCancel","");holding=false;holdTicks=0;state.addProperty("ammoHolding",false);state.addProperty("ammoProgress",0);}
    public static void renderCombat(net.minecraft.client.gui.GuiGraphics g){
        var font=Minecraft.getInstance().font;String balance="结算分 "+number(state,"earned")+"  |  战术点 "+number(state,"tactical")+"  |  兑换币 "+number(state,"coins");
        g.drawString(font,balance,Math.max(4,g.guiWidth()-font.width(balance)-8),8,0xFF8AF0A8);
        g.drawString(font,"B 战术轮盘 · 主1 / 主2 / 手枪",Math.max(4,g.guiWidth()-font.width("B 战术轮盘 · 主1 / 主2 / 手枪")-8),20,0xFFCCD5DE);
        if(holding||flag(state,"ammoHolding")){int x=g.guiWidth()/2-90,y=g.guiHeight()-72;double fraction=Math.min(1,(double)(holding?holdTicks:number(state,"ammoProgress"))/60);g.fill(x-3,y-14,x+183,y+10,0xDD101720);g.drawCenteredString(font,"按住换弹键补给 · 松开取消",g.guiWidth()/2,y-11,0xFFE6EBEF);g.fill(x,y,x+180,y+6,0xFF354253);g.fill(x,y,x+(int)(180*fraction),y+6,0xFF8AF0A8);}
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=new JsonObject();holding=false;holdTicks=0;}
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
    public static String batchStatus(JsonObject r){
        if(!text(r,"phase").equals("RUNNING")||number(r,"batchCount")==0)return "";
        return number(r,"bossCount")>0?"Boss ×"+number(r,"bossCount")+" 持续增援 · 残敌 "+number(r,"aliveCount"):"残敌 "+number(r,"aliveCount")+" · 待出 "+number(r,"batchUnspawned");
    }
    public static String summary(){var r=mine();if(r==null)return "封锁研究所 · 单人 / 1–4 人合作";String s=text(r,"phase");if(s.equals("LOADING"))return "等待队员加载地图…";if(s.equals("COUNTDOWN")||s.equals("REST"))return (s.equals("COUNTDOWN")?"首波准备":"下一波")+" · "+number(r,"seconds")+" 秒";if(s.equals("RUNNING")&&number(r,"batchCount")>0)return number(r,"wave")+"/"+number(r,"total")+" 波 · "+batchStatus(r);return phase(s)+" · "+number(r,"wave")+"/"+number(r,"total")+" 波 · 剩余 "+number(r,"remaining");}
    private static String phase(String s){return switch(s){case "BUILDING"->"地图生成";case "LOBBY"->"等待开局";case "LOADING"->"加载地图";case "COUNTDOWN"->"准备倒计时";case "REST"->"波间休整";case "RUNNING"->"战斗中";default->s;};}
}
