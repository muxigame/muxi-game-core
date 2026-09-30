package net.muxigame.core.taskssmoke;

import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Native client builds and ignites physical gates, checks paired travel and reconnect. */
@Mod(value="muxi_tasks_client_smoke",dist=Dist.CLIENT)
public final class DimensionsClientSmoke {
    private final List<String> passed=new ArrayList<>();
    private final Path server=Path.of(System.getProperty("muxi.qa.server"));
    private int ticks,stage,wait;
    private String address;
    private Vec3 returnGate;
    private int pendingNear=-1,moveWait;
    private boolean finished;
    public DimensionsClientSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void check(String name,boolean ok) {if(!ok)throw new AssertionError(name);passed.add(name);}
    private void connect(Minecraft mc) throws Exception {
        JsonObject config=JsonParser.parseString(Files.readString(server.resolve("e2e-address.json"))).getAsJsonObject();
        if(!config.get("host").getAsString().equals("127.0.0.1"))throw new AssertionError("Loopback only");
        address="127.0.0.1:"+config.get("port").getAsInt();
        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Local dimension QA",address,ServerData.Type.OTHER),false,null);
    }
    private void screenshot(Minecraft mc,String name) throws Exception {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(Path.of(name+".png")); }
    }
    private void items(Minecraft mc,String phase) {
        check(phase+" inventory synced",mc.player.getInventory().getItem(0).is(Items.DIAMOND)&&mc.player.getInventory().getItem(0).getCount()==23);
        check(phase+" xp synced",mc.player.experienceLevel==7);
    }
    private void near(Minecraft mc,int x) {
        // First step out of the portal plane, then walk alongside its solid frame.
        mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setPos(mc.player.getX(),mc.player.getY(),2.5);
        pendingNear=x;moveWait=0;
    }
    private void igniteAndEnter(Minecraft mc,int x) {
        BlockPos frame=new BlockPos(x,179,0);
        System.out.println("Ignite x="+x+" player="+mc.player.position()+" frame="+mc.level.getBlockState(frame));
        mc.player.getInventory().selected=1;
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(frame),Direction.UP,frame,false));
        mc.player.setPos(x+1.5,180,0.5);
    }
    private void finish(Minecraft mc,Throwable error) {
        finished=true;
        var result=new LinkedHashMap<String,Object>();result.put("success",error==null);result.put("passed",passed);
        if(error!=null){error.printStackTrace();result.put("error",error.toString());}
        try { Files.writeString(Path.of("client-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch(Exception writeError){writeError.printStackTrace();}
        if(mc.level!=null)mc.level.disconnect();
        mc.disconnect();mc.stop();
    }
    private void tick(ClientTickEvent.Post event) {
        if(finished)return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        try {
            if(ticks%200==0)System.out.println("Dimension E2E stage="+stage+" screen="+(mc.screen==null?"none":mc.screen.getClass().getName())+" overlay="+mc.getOverlay());
            if(ticks>3000)throw new AssertionError("E2E timed out at stage "+stage+", screen="+mc.screen);
            if(stage==0) {
                if(ticks<40||mc.getOverlay()!=null||mc.screen==null)return;
                check("native client window remains hidden",org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==0);
                connect(mc);stage=1;return;
            }
            if(stage==10) { if(++wait<50)return;connect(mc);stage=11;wait=0;return; }
            if(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)throw new AssertionError("Connection failed at stage "+stage);
            if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.screen!=null)return;
            if(pendingNear>=0&&++moveWait>=30) {
                double remaining=pendingNear-0.5-mc.player.getX();
                mc.player.setDeltaMovement(Vec3.ZERO);
                mc.player.setPos(mc.player.getX()+Math.max(-0.25,Math.min(0.25,remaining)),mc.player.getY(),2.5);
                if(Math.abs(remaining)<=0.25){mc.player.setPos(mc.player.getX(),mc.player.getY(),1.5);pendingNear=-1;}
            }
            if(++wait<120)return;wait=0;
            if(stage==1) {
                check("real client joined home",mc.level.dimension().equals(Level.OVERWORLD));items(mc,"home");
                check("new overworld registry synchronized",mc.getConnection().levels().contains(WorldDimensions.OVERWORLD));
                check("eternal night not registered",mc.getConnection().levels().stream().noneMatch(k->k.location().toString().equals("muxi_game_core:eternal_night")));
                // Verify the installed Xaero mixin method actually exists and only replaces visible labels.
                var method=Arrays.stream(xaero.map.gui.GuiMapSwitching.class.getDeclaredMethods())
                    .filter(m->m.getName().endsWith("muxi$dimensionLabel")).findFirst().orElseThrow();
                method.setAccessible(true);
                var instance=new xaero.map.gui.GuiMapSwitching(null);
                check("Xaero home label patched",method.invoke(instance,Level.OVERWORLD.location()).equals("家园"));
                check("Xaero survival label patched",method.invoke(instance,WorldDimensions.OVERWORLD.location()).equals("生存世界"));
                check("Xaero unrelated dimension name unchanged",method.invoke(instance,Level.NETHER.location()).equals("minecraft:the_nether"));
                screenshot(mc,"home");near(mc,2);stage=2;
            } else if(stage==2) {
                igniteAndEnter(mc,2);stage=3;
            } else if(stage==3) {
                check("built and ignited gate enters survival",mc.level.dimension().equals(WorldDimensions.OVERWORLD));items(mc,"survival");
                check("overworld is daytime",mc.level.isDay());screenshot(mc,"overworld");
                returnGate=mc.player.position();
                check("automatic home return gate generated",mc.level.getBlockState(mc.player.blockPosition()).is(net.muxigame.core.feature.dimensions.WorldPortals.HOME.get()));
                mc.player.setPos(returnGate.x,returnGate.y,returnGate.z+1.2);stage=4;
            } else if(stage==4) {
                mc.player.setPos(returnGate.x,returnGate.y,returnGate.z);stage=5;
            } else if(stage==5) {
                check("paired gate returns to original home",mc.level.dimension().equals(Level.OVERWORLD)&&mc.player.position().distanceTo(new Vec3(3.5,180,0.5))<1);
                items(mc,"returned home");mc.player.setPos(3.5,180,1.7);stage=6;
            } else if(stage==6) {
                mc.player.setPos(3.5,180,0.5);stage=7;
            } else if(stage==7) {
                check("original gate can be reused",mc.level.dimension().equals(WorldDimensions.OVERWORLD));
                near(mc,14);stage=8;
            } else if(stage==8) {
                igniteAndEnter(mc,14);stage=9;
            } else if(stage==9) {
                check("player built quartz return gate reaches home",mc.level.dimension().equals(Level.OVERWORLD));
                items(mc,"quartz return");returnGate=mc.player.position();
                check("new reverse gate generated in home",mc.level.getBlockState(mc.player.blockPosition()).is(net.muxigame.core.feature.dimensions.WorldPortals.OVERWORLD.get()));
                mc.player.setPos(returnGate.x,returnGate.y,returnGate.z+1.2);stage=14;
            } else if(stage==14) {
                mc.player.setPos(returnGate.x,returnGate.y,returnGate.z);stage=15;
            } else if(stage==15) {
                check("new paired gate returns to survival",mc.level.dimension().equals(WorldDimensions.OVERWORLD));
                mc.level.disconnect();mc.disconnect();stage=10;wait=0;
            } else if(stage==11) {
                check("relogin retains survival dimension",mc.level.dimension().equals(WorldDimensions.OVERWORLD));items(mc,"relogin");
                mc.player.setPos(15.5,180,1.7);stage=12;
            } else if(stage==12) {
                mc.player.setPos(15.5,180,0.5);stage=13;
            } else if(stage==13) {
                check("physical home gate works after relog",mc.level.dimension().equals(Level.OVERWORLD));items(mc,"final home");finish(mc,null);
            }
        } catch(Throwable error) {
            if(mc.player!=null&&mc.level!=null)System.out.println("Failure location="+mc.player.position()+" dimension="+mc.level.dimension()+" block="+mc.level.getBlockState(mc.player.blockPosition()));
            finish(mc,error);
        }
    }
}
