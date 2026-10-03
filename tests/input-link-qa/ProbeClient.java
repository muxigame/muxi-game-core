package net.muxigame.inputlinkqa;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.muxigame.core.client.input.GameplayInputPriority;
import net.muxigame.core.feature.input.GameInputContextState;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@Mod(value="input_link_qa",dist=Dist.CLIENT)
public final class ProbeClient {
    int last=-1,ticks,finish;JsonObject pending;int gun,sent,swap;
    public ProbeClient(){if(ProbeFiles.enabled())NeoForge.EVENT_BUS.addListener(this::tick);}
    public static boolean clearFrame()throws Exception{var f=GameplayInputPriority.class.getDeclaredField("frame");f.setAccessible(true);return f.get(null)==null;}
    void tick(ClientTickEvent.Post ignored){
        ticks++;var mc=Minecraft.getInstance();String role=System.getProperty("qa.local.role");
        try{
            if(pending!=null&&ticks>=finish){
                pending.addProperty("gunLogicEntries",ProbeCounters.gun.get()-gun);pending.addProperty("outbreakInteractSent",ProbeCounters.sent.get()-sent);pending.addProperty("swapPacketsSent",ProbeCounters.swap.get()-swap);
                pending.addProperty("frameCleared",clearFrame());pending.addProperty("contextClaimsOutbreak",mc.level!=null&&GameInputContextState.claimsOutbreak(mc.level.dimension().location().toString()));pending.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());pending.addProperty("windowActive",mc.isWindowActive());pending.add("pressFrame",ProbeCounters.frame.deepCopy());
                var maps=new JsonObject();for(var k:mc.options.keyMappings)if(k.getKey().getValue()==66||k.getName().equals("key.swapOffhand")||k.getName().equals("key.playerlist")||k.getName().equals("key.tacz.interact.desc")){var row=new JsonObject();row.addProperty("key",k.getKey().getValue());row.addProperty("modifier",k.getKeyModifier().name());row.addProperty("down",k.isDown());maps.add(k.getName(),row);}pending.add("mappings",maps);
                ProbeFiles.write("inputlink-result-"+role+"-"+pending.get("id").getAsInt()+".json",pending);pending=null;
            }
            if(pending!=null)return;var cmd=ProbeFiles.read("inputlink-command-"+role+".json");if(cmd==null||cmd.get("id").getAsInt()<=last)return;last=cmd.get("id").getAsInt();
            pending=new JsonObject();pending.addProperty("id",last);pending.addProperty("ok",true);pending.addProperty("nativeKeyboardCallback",true);pending.addProperty("hardwareModifierStateInjected",false);pending.addProperty("key",cmd.get("key").getAsInt());pending.addProperty("modifiers",cmd.get("modifiers").getAsInt());gun=ProbeCounters.gun.get();sent=ProbeCounters.sent.get();swap=ProbeCounters.swap.get();ProbeCounters.frame=new JsonObject();
            int key=cmd.get("key").getAsInt(),mods=cmd.get("modifiers").getAsInt();if(mc.player==null||mc.level==null||mc.getConnection()==null)throw new IllegalStateException("Actual connected player required");
            if(key>=0){mc.setScreen(null);long window=mc.getWindow().getWindow();mc.keyboardHandler.keyPress(window,key,0,1,mods);if(cmd.has("repeat")&&cmd.get("repeat").getAsBoolean())mc.keyboardHandler.keyPress(window,key,0,2,mods);mc.keyboardHandler.keyPress(window,key,0,0,mods);}
            finish=ticks+8;
        }catch(Exception failure){if(pending==null)pending=new JsonObject();pending.addProperty("id",last);pending.addProperty("ok",false);pending.addProperty("error",failure.toString());try{ProbeFiles.write("inputlink-result-"+role+"-"+last+".json",pending);}catch(Exception busy){}pending=null;}
    }
}
