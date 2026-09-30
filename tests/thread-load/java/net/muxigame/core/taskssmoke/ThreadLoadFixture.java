package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.muxigame.core.threading.DimensionThreads;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Actual native clients and native block entities; only installed in disposable test servers. */
@Mod("muxi_thread_load")
public final class ThreadLoadFixture {
    private final int playerCount=Integer.getInteger("muxi.qa.players",2);
    private final int spacing=Integer.getInteger("muxi.qa.spacing",2048);
    private MinecraftServer server;
    private int tick, phaseTick;
    private volatile String phase="waiting";
    private long serverStart,previousStart,lastIntervalNanos,tickStartEpochMillis;
    private final List<Map<String,Object>> tickTimeline=new ArrayList<>();
    private final Map<String,Object> exploration=new LinkedHashMap<>();
    private final List<Map<String,Object>> routeProgress=new ArrayList<>();
    private final Map<String,Long> firstArrival=new TreeMap<>();
    private final Map<String,Long> phaseStartEpochMillis=new LinkedHashMap<>();
    private final Map<ServerLevel,Long> levelStarts=new ConcurrentHashMap<>();
    private final Map<String,List<Double>> samples=new ConcurrentHashMap<>();
    private final List<String> checks=new ArrayList<>();
    private final Set<UUID> initializedPlayers=new HashSet<>();
    private boolean done;
    private final StabilityScenario stability=new StabilityScenario();
    private final int route=StabilityScenario.ENABLED?1024:384;
    public ThreadLoadFixture() {
        NeoForge.EVENT_BUS.addListener(this::start);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::pre);
        NeoForge.EVENT_BUS.addListener(this::post);
        NeoForge.EVENT_BUS.addListener(this::levelPre);
        NeoForge.EVENT_BUS.addListener(this::levelPost);
    }
    private void check(String name,boolean ok) { if(!ok)throw new AssertionError(name);checks.add(name); }
    private List<ServerLevel> levels() {return List.of(server.overworld(),server.getLevel(WorldDimensions.OVERWORLD));}
    private List<String> roles() {
        var names=new ArrayList<String>();
        for(int i=0;i<playerCount/2;i++)for(String base:List.of("Home","Survival"))names.add(base+(i==0?"":Integer.toString(i+1)));
        return names;
    }
    private boolean allMarkers(String suffix){return roles().stream().allMatch(role->Files.exists(Path.of(role+suffix)));}
    private int lane(String role){String digits=role.replaceAll("\\D","");return digits.isEmpty()?0:Integer.parseInt(digits)-1;}
    private void state(String next) throws Exception {
        phase=next;phaseTick=0;
        phaseStartEpochMillis.put(next,System.currentTimeMillis());
        Files.writeString(Path.of("load-phase.tmp"),next);Files.move(Path.of("load-phase.tmp"),Path.of("load-phase.txt"),StandardCopyOption.REPLACE_EXISTING);
        System.out.println("THREAD_LOAD_PHASE "+next);
        if(StabilityScenario.ENABLED){
            var timing=new TreeMap<String,Object>();samples.forEach((key,value)->timing.put(key,summary(value)));
            Files.writeString(Path.of("load-checkpoint.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "complete",false,"phase",phase,"stability",stability.report(),"timing",timing,
                "phaseStartEpochMillis",phaseStartEpochMillis,"tickTimeline",tickTimeline,
                "routeProgress",routeProgress,"firstArrivalEpochMillis",firstArrival)));
        }
    }
    private BlockPos furnace(int i) {return new BlockPos((i%8)*2,180,(i/8)*2);}
    private BlockPos chest(int i) {return new BlockPos((i%8)*2,180,20+(i/8)*2);}
    private void armMachines() {
        // Client resource loading can take minutes. Begin with fresh active inventories
        // only after both players join, so the benchmark never measures drained hoppers.
        for(var level:levels())for(int i=0;i<64;i++) {
            var f=(FurnaceBlockEntity)level.getBlockEntity(furnace(i));f.clearContent();
            f.setItem(0,new ItemStack(Items.RAW_IRON,64));f.setItem(1,new ItemStack(Items.COAL,64));
            ((Container)level.getBlockEntity(chest(i))).clearContent();
            var h=(HopperBlockEntity)level.getBlockEntity(chest(i).above());h.clearContent();
            for(int slot=0;slot<h.getContainerSize();slot++)h.setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        }
    }
    private void start(ServerStartedEvent event) {
        server=event.getServer();
        try {
            SableCacheRegression.verify();
            for(var level:levels()) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(StabilityScenario.ENABLED,server);
                level.setDayTime(6000);
                level.setDefaultSpawnPos(new BlockPos(0,200,0),0);
                for(int x=0;x<=2;x++)for(int z=0;z<=2;z++)level.setChunkForced(x,z,true);
                if(Boolean.getBoolean("muxi.qa.verifySave"))continue;
                // Prepare fixed login locations before clients connect. Cold synchronous login
                // teleports must not starve other clients' configuration handshakes.
                for(int lane=1;lane<playerCount/2;lane++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
                    level.getChunk(x,lane*spacing/16+z);
                for(int i=0;i<64;i++) {
                    var p=furnace(i);level.setBlockAndUpdate(p,Blocks.FURNACE.defaultBlockState());
                    var f=(FurnaceBlockEntity)level.getBlockEntity(p);f.setItem(0,new ItemStack(Items.RAW_IRON,64));f.setItem(1,new ItemStack(Items.COAL,64));
                    p=chest(i);level.setBlockAndUpdate(p,Blocks.CHEST.defaultBlockState());
                    level.setBlockAndUpdate(p.above(),Blocks.HOPPER.defaultBlockState());
                    ((HopperBlockEntity)level.getBlockEntity(p.above())).setItem(0,new ItemStack(Items.COBBLESTONE,64));
                }
                var motor=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:creative_motor"));
                var shaft=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"));
                check("Create motor registered",motor!=Blocks.AIR);
                for(int row=0;row<8;row++) {
                    level.setBlockAndUpdate(new BlockPos(row*3,185,0),motor.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
                    for(int z=1;z<=16;z++)level.setBlockAndUpdate(new BlockPos(row*3,185,z),shaft.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
                }
            }
            if(Boolean.getBoolean("muxi.qa.verifySave")) {
                for(var level:levels())for(int i=0;i<64;i++) {
                    check("saved furnace output restored "+level.dimension()+"/"+i,((Container)level.getBlockEntity(furnace(i))).getItem(2).is(Items.IRON_INGOT));
                    check("saved chest contents restored "+level.dimension()+"/"+i,((Container)level.getBlockEntity(chest(i))).getItem(0).is(Items.COBBLESTONE));
                }
                if(StabilityScenario.ENABLED){state("verify-stability-save");return;}
                Files.writeString(Path.of("load-save-result.json"),"{\"success\":true}");server.halt(false);return;
            }
            state("waiting");Files.writeString(Path.of("e2e-ready"),"ready");
        }catch(Throwable error){finish(error);}
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.getGameProfile().getName().startsWith("MuxiQA"))return;
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
        if(initializedPlayers.add(player.getUUID())&&!player.getPersistentData().getBoolean("load_initialized")) {
            String role=player.getGameProfile().getName().substring("MuxiQA".length());
            var level=role.startsWith("Home")?server.overworld():server.getLevel(WorldDimensions.OVERWORLD);
            player.teleportTo(level,.5,200,.5+lane(role)*spacing,0,0);
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,23));player.giveExperienceLevels(7);
            player.getPersistentData().putBoolean("load_initialized",true);
        }
    }
    private void pre(ServerTickEvent.Pre event){serverStart=System.nanoTime();tickStartEpochMillis=System.currentTimeMillis();lastIntervalNanos=previousStart==0?0:serverStart-previousStart;if(previousStart!=0)sample("tickInterval",lastIntervalNanos);previousStart=serverStart;}
    private void levelPre(LevelTickEvent.Pre event){if(event.getLevel() instanceof ServerLevel level)levelStarts.put(level,System.nanoTime());}
    private void levelPost(LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level){var t=levelStarts.remove(level);if(t!=null)sample(level.dimension().location().toString(),System.nanoTime()-t);}}
    private void sample(String key,long nanos){if(phase.equals("machines")||phase.equals("exploration")||phase.equals("stability")||phase.equals("survival-combat"))samples.computeIfAbsent(phase+"/"+key,k->Collections.synchronizedList(new ArrayList<>())).add(nanos/1e6);}
    private void post(ServerTickEvent.Post event) {
        if(done)return;
        if(phase.equals("machines")||phase.equals("exploration")||phase.equals("stability")||phase.equals("survival-combat"))tickTimeline.add(Map.of("phase",phase,"epochMillis",tickStartEpochMillis,"intervalMs",lastIntervalNanos/1e6,"workMs",(System.nanoTime()-serverStart)/1e6));
        sample("server",System.nanoTime()-serverStart);tick++;phaseTick++;
        try {
            if(phase.equals("verify-stability-save")){
                if(phaseTick>=100){StabilityScenario.verifySave(server);Files.writeString(Path.of("load-save-result.json"),"{\"success\":true,\"persistedModEntities\":8}");done=true;server.halt(false);}
                return;
            }
            if(StabilityScenario.ENABLED&&tick%100==0)StabilityScenario.assertNoSuppressedErrors();
            if(tick>36000)throw new AssertionError("Load fixture timed out in "+phase);
            if(phase.equals("exploration")) {
                for(var player:server.getPlayerList().getPlayers()) {
                    String name=player.getGameProfile().getName();
                    if(player.getX()>=route-1)firstArrival.putIfAbsent(name,System.currentTimeMillis());
                    if(phaseTick%20==0)routeProgress.add(Map.of("player",name,"epochMillis",System.currentTimeMillis(),"x",player.getX(),"z",player.getZ()));
                }
                if(StabilityScenario.ENABLED&&phaseTick%100==0){
                    var positions=new ArrayList<Map<String,Object>>();
                    for(var player:server.getPlayerList().getPlayers())positions.add(Map.of("player",player.getGameProfile().getName(),"x",player.getX(),"y",player.getY(),"z",player.getZ(),"ahead",player.serverLevel().getBlockState(player.blockPosition().east()).toString()));
                    Files.writeString(Path.of("route-live.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("tick",phaseTick,"positions",positions,"arrived",firstArrival)));
                }
                if(phaseTick>7200)throw new AssertionError("Fixed exploration route incomplete after 7200 server ticks");
            }
            if(phase.equals("waiting")&&server.getPlayerCount()==playerCount&&allMarkers("-ready"))state("warmup");
            else if(phase.equals("warmup")&&phaseTick>=600){armMachines();state("machines");}
            else if(phase.equals("machines")&&phaseTick>=400){exploration.put("before",DimensionThreads.metrics(server));if(StabilityScenario.ENABLED){stability.begin(server,spacing);state("stability");}else state("exploration");}
            else if(phase.equals("stability")){
                stability.tick(server,spacing,phaseTick);
                if(phaseTick>=2400){stability.validate(server);stability.beginSurvivalCombat(server,spacing);state("survival-combat");}
            }
            else if(phase.equals("survival-combat")&&phaseTick>=600){stability.validateSurvivalCombat(server);stability.explore(server,spacing);state("exploration");}
            else if(phase.equals("exploration")&&phaseTick>=600&&firstArrival.size()==playerCount) {
                exploration.put("measuredTicks",phaseTick);
                exploration.put("firstArrivalEpochMillis",firstArrival);
                exploration.put("routeProgress",routeProgress);
                if(StabilityScenario.ENABLED){
                    var playerState=new TreeMap<String,Object>();
                    for(var player:server.getPlayerList().getPlayers()){
                        var diamonds=player.getInventory().getItem(0);
                        check("initial test diamonds retained before transfer "+player.getGameProfile().getName(),diamonds.is(Items.DIAMOND)&&diamonds.getCount()>=23);
                        playerState.put(player.getGameProfile().getName(),Map.of("experienceLevel",player.experienceLevel,"diamondCount",diamonds.getCount()));
                    }
                    Files.writeString(Path.of("stability-transfer-state.json"),new GsonBuilder().create().toJson(playerState));
                }
                state("transfer");
                exploration.put("after",DimensionThreads.metrics(server));
                for(var player:List.copyOf(server.getPlayerList().getPlayers())) {
                    exploration.put(player.getGameProfile().getName()+"FinalX",player.getX());
                    exploration.put(player.getGameProfile().getName()+"FinalZ",player.getZ());
                    String role=player.getGameProfile().getName().substring("MuxiQA".length());
                    check("separate exploration lane "+role,Math.abs(player.getZ()-(.5+lane(role)*spacing))<2);
                    check("player traversed fixed "+route+"-block route "+player.getGameProfile().getName(),player.getX()>=route-1);
                    var target=player.serverLevel()==server.overworld()?server.getLevel(WorldDimensions.OVERWORLD):server.overworld();
                    player.teleportTo(target,.5,StabilityScenario.ENABLED?240:200,.5,0,0);
                }
            } else if(phase.equals("transfer")&&allMarkers("-transferred")) state("reconnect");
            else if(phase.equals("reconnect")&&allMarkers("-reconnected")) {
                for(var level:levels()) {
                    int smelted=0,moved=0,rotating=0;
                    for(int i=0;i<64;i++) {
                        var f=(Container)level.getBlockEntity(furnace(i));if(f.getItem(2).is(Items.IRON_INGOT))smelted++;
                        var c=(Container)level.getBlockEntity(chest(i));if(c.getItem(0).is(Items.COBBLESTONE))moved++;
                    }
                    for(int row=0;row<8;row++)for(int z=1;z<=16;z++) {
                        var be=level.getBlockEntity(new BlockPos(row*3,185,z));
                        if(((Number)be.getClass().getMethod("getSpeed").invoke(be)).floatValue()!=0)rotating++;
                    }
                    check("64 furnaces produced iron "+level.dimension(),smelted==64);
                    check("64 hoppers transferred items "+level.dimension(),moved==64);
                    check("128 Create shafts rotating "+level.dimension(),rotating==128);
                }
                if(StabilityScenario.ENABLED)stability.prepareSave(server);
                state("done");
            } else if(phase.equals("done")&&phaseTick>=60)finish(null);
        }catch(Throwable error){finish(error);}
    }
    private Map<String,Object> summary(List<Double> values) {
        var sorted=values.stream().sorted().toList();int n=sorted.size();
        return Map.of("count",n,"meanMs",sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            "p95Ms",sorted.get((int)((n-1)*.95)),"p99Ms",sorted.get((int)((n-1)*.99)),"maxMs",sorted.getLast(),
            "over50ms",sorted.stream().filter(v->v>50).count(),"over100ms",sorted.stream().filter(v->v>100).count());
    }
    private void finish(Throwable error) {
        if(done)return;done=true;
        var out=new LinkedHashMap<String,Object>();out.put("success",error==null);out.put("passed",checks);
        if(StabilityScenario.ENABLED){exploration.put("routeProgress",routeProgress);exploration.put("firstArrivalEpochMillis",firstArrival);}
        if(error!=null){error.printStackTrace();out.put("error",error.toString());}
        var timing=new TreeMap<String,Object>();samples.forEach((key,value)->timing.put(key,summary(value)));out.put("timing",timing);
        out.put("threading",DimensionThreads.metrics(server));out.put("nativePlayers",playerCount);out.put("spacing",spacing);out.put("phase",phase);out.put("exploration",exploration);out.put("phaseStartEpochMillis",phaseStartEpochMillis);
        out.put("tickTimeline",tickTimeline);
        if(StabilityScenario.ENABLED)out.put("stability",stability.report());
        try {Files.writeString(Path.of("load-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));}catch(Exception e){throw new RuntimeException(e);}
        server.halt(false);
    }
}
