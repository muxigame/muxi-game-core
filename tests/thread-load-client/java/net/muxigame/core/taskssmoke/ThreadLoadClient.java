package net.muxigame.core.taskssmoke;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
@Mod(value="muxi_tasks_client_smoke",dist=Dist.CLIENT)
public final class ThreadLoadClient {
    private final Path server=Path.of(System.getProperty("muxi.qa.server"));
    private final String role=System.getProperty("muxi.qa.loadRole");
    private int tick,stage,wait;private boolean done,ready;private String phase="waiting";
    private long explorationStarted,arrived;
    private int visibleSamples,missingCurrentChunk;
    private int attacks,placements,breaks;
    private int combatTicks;
    private final List<String> combatScreenshots=new ArrayList<>();
    private boolean stability;
    private int route=384;
    private final List<String> passed=new ArrayList<>();
    public ThreadLoadClient(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void connect(Minecraft mc)throws Exception{
        var conf=JsonParser.parseString(Files.readString(server.resolve("e2e-address.json"))).getAsJsonObject();
        if(!conf.get("host").getAsString().equals("127.0.0.1"))throw new AssertionError("loopback only");
        String address="127.0.0.1:"+conf.get("port").getAsInt();
        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Isolated load",address,ServerData.Type.OTHER),false,null);
    }
    private void check(String message,boolean ok){if(!ok)throw new AssertionError(message);passed.add(message);}
    private boolean connectionTurn(boolean reconnect)throws Exception {
        var roles=JsonParser.parseString(Files.readString(server.resolve("load-roles.json"))).getAsJsonArray();
        String previous=null;
        for(var entry:roles) {
            String name=entry.getAsString();
            if(!reconnect&&!Files.exists(server.resolve(name+"-boot-ready")))return false;
        }
        for(var entry:roles) {
            String name=entry.getAsString();
            if(name.equals(role))return previous==null||Files.exists(server.resolve(previous+(reconnect?"-reconnected":"-ready")));
            previous=name;
        }
        throw new AssertionError("Missing player role "+role);
    }
    private void player(Minecraft mc)throws Exception{
        var expected=stability?JsonParser.parseString(Files.readString(server.resolve("stability-transfer-state.json"))).getAsJsonObject().getAsJsonObject("MuxiQA"+role):null;
        int diamonds=expected==null?23:expected.get("diamondCount").getAsInt();
        int experience=expected==null?7:expected.get("experienceLevel").getAsInt();
        check("test diamond stack intact",mc.player.getInventory().getItem(0).is(Items.DIAMOND)&&mc.player.getInventory().getItem(0).getCount()==diamonds);
        check("experience intact",mc.player.experienceLevel==experience);
        check("destination dimension synchronized",mc.level.dimension().equals(role.startsWith("Home")?WorldDimensions.OVERWORLD:Level.OVERWORLD));
    }
    private void tick(ClientTickEvent.Post event){
        if(done)return;var mc=Minecraft.getInstance();tick++;
        try{
            if(tick>36000)throw new AssertionError("client timeout stage "+stage+" phase "+phase);
            if(stage==0){
                if(tick<40||mc.getOverlay()!=null||mc.screen==null)return;
                if(!Files.exists(server.resolve(role+"-boot-ready")))Files.writeString(server.resolve(role+"-boot-ready"),"title-ready");
                if(!connectionTurn(false))return;
                connect(mc);stage=1;return;
            }
            if(stage==4){if(++wait<60||!connectionTurn(true))return;connect(mc);stage=5;return;}
            if(stage>=1&&tick%10==0)phase=Files.readString(server.resolve("load-phase.txt")).trim();
            if(stability&&phase.equals("survival-combat")&&mc.screen instanceof DeathScreen){mc.player.respawn();mc.setScreen(null);return;}
            if(mc.screen instanceof DisconnectedScreen || (mc.screen!=null && mc.screen.getClass().getSimpleName().contains("DisconnectedScreen")))
                throw new AssertionError("disconnected stage "+stage+" screen "+mc.screen.getClass().getName());
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.getOverlay()!=null)return;
            if(!ready){stability=Files.exists(server.resolve("stability.json"));if(stability)route=1024;Files.writeString(server.resolve(role+"-ready"),"in-game");ready=true;}
            if(tick%10==0)phase=Files.readString(server.resolve("load-phase.txt")).trim();
            if(phase.equals("stability")){
                combatTicks++;
                if(combatTicks==200||combatTicks==1300||combatTicks==1900){
                    String filename="combat-"+role+"-"+combatTicks+".png";
                    try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(Path.of(filename));}
                    combatScreenshots.add(filename);
                }
                mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);
                if(tick%10==0){
                    mc.player.getInventory().selected=2;
                    var nearby=mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,mc.player.getBoundingBox().inflate(3),e->e.isAlive());
                    if(!nearby.isEmpty()){mc.gameMode.attack(mc.player,nearby.getFirst());mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);attacks++;}
                }
                if(tick%6==0){
                    // Creative swords deliberately cannot break blocks. Select the building slot for both operations.
                    mc.player.getInventory().selected=1;
                    var base=mc.player.blockPosition().below().offset(2,0,0);
                    var pos=base.above();
                    if(!mc.level.getBlockState(pos).isAir()){mc.gameMode.startDestroyBlock(pos,net.minecraft.core.Direction.UP);breaks++;}
                    else if(!mc.level.getBlockState(base).isAir()){
                        mc.player.getInventory().selected=1;
                        mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(base).add(0,.5,0),net.minecraft.core.Direction.UP,base,false));placements++;
                    }
                }
            }
            if(phase.equals("exploration")){
                if(explorationStarted==0)explorationStarted=System.currentTimeMillis();
                visibleSamples++;
                if(!mc.level.hasChunk(mc.player.chunkPosition().x,mc.player.chunkPosition().z))missingCurrentChunk++;
                if(mc.player.getX()>=route-1&&arrived==0)arrived=System.currentTimeMillis();
                mc.player.getAbilities().flying=true;mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setPos(Math.min(route+.5,mc.player.getX()+.8),stability?336:200,mc.player.getZ());
            }
            if(phase.equals("transfer")&&stage==1){
                var expected=role.startsWith("Home")?WorldDimensions.OVERWORLD:Level.OVERWORLD;
                if(!mc.level.dimension().equals(expected))return;
                player(mc);Files.writeString(server.resolve(role+"-transferred"),"verified");stage=2;
            }else if(phase.equals("reconnect")&&stage==2){mc.level.disconnect();mc.disconnect();stage=4;wait=0;}
            else if(phase.equals("reconnect")&&stage==5){
                player(mc);Files.writeString(server.resolve(role+"-reconnected"),"verified");stage=6;
            }else if(phase.equals("done")&&stage==6){
                try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){screenshot.writeToFile(Path.of("load-"+role+".png"));}
                finish(mc,null);
            }
        }catch(Throwable error){finish(mc,error);}
    }
    private void finish(Minecraft mc,Throwable error){
        done=true;var out=new LinkedHashMap<String,Object>();out.put("success",error==null);out.put("passed",passed);out.put("role",role);
        out.put("explorationStartedEpochMillis",explorationStarted);out.put("clientArrivalEpochMillis",arrived);
        out.put("nativeAttackAttempts",attacks);out.put("nativePlaceAttempts",placements);out.put("nativeBreakAttempts",breaks);
        out.put("combatScreenshots",combatScreenshots);
        out.put("currentChunkSamples",visibleSamples);out.put("missingCurrentChunkSamples",missingCurrentChunk);
        if(error!=null){error.printStackTrace();out.put("error",error.toString());}
        try{Files.writeString(Path.of("client-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));}catch(Exception e){throw new RuntimeException(e);}
        if(mc.level!=null)mc.level.disconnect();mc.disconnect();mc.stop();
    }
}
