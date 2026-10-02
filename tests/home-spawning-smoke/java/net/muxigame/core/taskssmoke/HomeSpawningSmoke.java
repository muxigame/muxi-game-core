package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.storage.ServerLevelData;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Native server execution, deliberately separate from production mods and the existing task fixture. */
@Mod("muxi_tasks_smoke")
public final class HomeSpawningSmoke {
    private final List<String> passed = new ArrayList<>();
    private final Map<String,Integer> finalized = new TreeMap<>(), joined = new TreeMap<>();
    private final Map<String,Object> observations = new LinkedHashMap<>();
    private int ticks;
    private boolean forcePhantoms, cancelInitializationOnly;
    private boolean allowReinforcementCandidate;
    private static final BlockPos BASE = new BlockPos(0, 200, 0);

    public HomeSpawningSmoke() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::stopped);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (PlayerSpawnPhantomsEvent event) -> {
            if (forcePhantoms) event.setResult(PlayerSpawnPhantomsEvent.Result.ALLOW);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (FinalizeSpawnEvent event) -> {
            if (cancelInitializationOnly && event.getSpawnType()==MobSpawnType.MOB_SUMMONED) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (MobSpawnEvent.SpawnPlacementCheck event) -> {
            if(allowReinforcementCandidate && event.getSpawnType()==MobSpawnType.REINFORCEMENT)
                event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (FinalizeSpawnEvent event) -> {
            String key=event.getLevel().getLevel().dimension().location()+"/"+event.getSpawnType()+"/"+event.isSpawnCancelled();
            finalized.merge(key,1,Integer::sum);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, (EntityJoinLevelEvent event) -> {
            if(!event.isCanceled()) joined.merge(event.getLevel().dimension().location()+"/"+BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()),1,Integer::sum);
        });
    }

    private void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        passed.add(name);
    }
    private int count(ServerLevel level, EntityType<?> type) {
        // A level's visible entity lookup excludes non-ticking sections, even after successful insertion.
        // Count accepted native join events rather than requiring a player's client tracking visibility.
        return joined.getOrDefault(level.dimension().location()+"/"+BuiltInRegistries.ENTITY_TYPE.getKey(type),0);
    }
    private static boolean expected(MobSpawnType reason, boolean home) {
        return home && Set.of(MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT).contains(reason);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f=type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private ServerPlayer player(MinecraftServer server, ServerLevel level) {
        var profile=new GameProfile(UUID.randomUUID(),"SpawnQA");
        ServerPlayer p=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        Connection connection=new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(server,connection,p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) {}
        };
        p.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+.5);
        p.getStats().setValue(p,Stats.CUSTOM.get(Stats.TIME_SINCE_REST),10_000_000);
        level.addNewPlayer(p);
        return p;
    }
    private void pad(ServerLevel level) {
        // Allow the real entity section manager to make these chunks visible before insertion assertions.
        for(int x=-1;x<=0;x++) for(int z=-1;z<=0;z++) level.setChunkForced(x,z,true);
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) {
            level.setBlock(BASE.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=3;y++) level.setBlock(BASE.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
    }
    private void eventMatrix(ServerLevel level) {
        boolean home=level.dimension().equals(Level.OVERWORLD);
        for(MobSpawnType reason:MobSpawnType.values()) {
            boolean blocked=expected(reason,home);
            var zombie=EntityType.ZOMBIE.create(level); zombie.moveTo(BASE,0,0);
            Mob mob=reason==MobSpawnType.EVENT?EntityType.WOLF.create(level):zombie;
            check(level.dimension().location()+" native placement hook "+reason,
                EventHooks.checkSpawnPlacements(mob.getType(),level,reason,BASE,RandomSource.create(17),true)==!blocked);
            var position=new MobSpawnEvent.PositionCheck(mob,level,reason,null);
            NeoForge.EVENT_BUS.post(position);
            check(level.dimension().location()+" position "+reason,position.getResult()==(blocked?MobSpawnEvent.PositionCheck.Result.FAIL:MobSpawnEvent.PositionCheck.Result.DEFAULT));
            // EVENT zombie is the native village siege; use a wolf for the deliberate EVENT exception matrix.
            mob.moveTo(BASE.offset(1,0,1),0,0);
            EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),reason,null);
            check(level.dimension().location()+" finalize "+reason,mob.isSpawnCancelled()==blocked);
            int before=count(level,mob.getType());
            boolean added=level.addFreshEntity(mob);
            check(level.dimension().location()+" insert "+reason,added==!blocked && count(level,mob.getType())-before==(blocked?0:1));
            if(added) mob.discard(); // Only fixture-owned control mobs, after insertion assertions.
        }
    }
    private void nativePhantoms(ServerLevel level) {
        level.setDayTime(18000); level.updateSkyBrightness();
        level.random.setSeed(91823);
        boolean home=level.dimension().equals(Level.OVERWORLD);
        int before=count(level,EntityType.PHANTOM), attempts=0;
        // Actual vanilla criteria: night, sky, unslept player, native RNG, and native spawner.
        while(attempts++<60 && (home || count(level,EntityType.PHANTOM)==before))
            new PhantomSpawner().tick(level,true,true);
        int delta=count(level,EntityType.PHANTOM)-before;
        check(level.dimension().location()+" real unslept night PhantomSpawner",home?delta==0:delta>0);
        observations.put(level.dimension().location()+"/nativePhantoms",Map.of("attempts",attempts-1,"inserted",delta,"skyDarken",level.getSkyDarken()));
        // A competing mod ALLOW must still not permit home ambient phantoms.
        forcePhantoms=true;
        before=count(level,EntityType.PHANTOM);
        new PhantomSpawner().tick(level,true,true);
        delta=count(level,EntityType.PHANTOM)-before;
        forcePhantoms=false;
        check(level.dimension().location()+" PhantomSpawner competing ALLOW",home?delta==0:delta>0);
    }
    private void nativePatrol(ServerLevel level) throws Exception {
        var method=PatrolSpawner.class.getDeclaredMethod("spawnPatrolMember",ServerLevel.class,BlockPos.class,RandomSource.class,boolean.class);
        method.setAccessible(true);
        int before=count(level,EntityType.PILLAGER);
        method.invoke(new PatrolSpawner(),level,BASE,RandomSource.create(17),true);
        int delta=count(level,EntityType.PILLAGER)-before;
        check(level.dimension().location()+" native patrol helper insertion",level.dimension().equals(Level.OVERWORLD)?delta==0:delta>0);
    }
    private void nativeTraders(ServerLevel level) {
        for(String id:List.of("minecraft:wandering_trader","minecraft:trader_llama","goblintraders:goblin_trader","goblintraders:vein_goblin_trader","minecraft:zombie")) {
            var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            check("audited trader registered "+id,type!=EntityType.PIG);
            int before=count(level,type);
            type.spawn(level,BASE.offset(2,0,2),MobSpawnType.EVENT);
            int delta=count(level,type)-before;
            check(level.dimension().location()+" native EntityType.spawn EVENT "+id,level.dimension().equals(Level.OVERWORLD)?delta==0:delta==1);
            // Same species can still be explicitly summoned at home.
            before=count(level,type);
            type.spawn(level,BASE.offset(-2,0,-2),MobSpawnType.COMMAND);
            check(level.dimension().location()+" explicit trader COMMAND "+id,count(level,type)==before+1);
        }
    }
    private void nativeReinforcementPlacement(ServerLevel level) {
        allowReinforcementCandidate=true;
        boolean result=SpawnPlacements.checkSpawnRules(EntityType.ZOMBIE,level,MobSpawnType.REINFORCEMENT,BASE,RandomSource.create(17));
        allowReinforcementCandidate=false;
        check(level.dimension().location()+" native reinforcement placement overrides mod ALLOW",result!=level.dimension().equals(Level.OVERWORLD));
    }
    private void goblinTimers(MinecraftServer server) throws Exception {
        Class<?> type=Class.forName("com.mrcrayfish.goblintraders.spawner.GoblinTraderSpawner");
        Object timer=((Optional<?>)type.getMethod("get",MinecraftServer.class,EntityType.class).invoke(null,server,BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("goblintraders:goblin_trader")))).orElseThrow();
        type.getMethod("serverTick").invoke(timer);
        Field extra=Arrays.stream(type.getDeclaredFields()).filter(f->f.getName().endsWith("muxi$explorationSpawners")).findFirst().orElseThrow(); extra.setAccessible(true);
        Map<?,?> timers=(Map<?,?>)extra.get(timer);
        check("goblin home dispatch retains both exploration timers",timers.keySet().containsAll(WorldDimensions.EXPLORATION));
        for(var key:WorldDimensions.EXPLORATION) field(type,"runDelay").setInt(timers.get(key),200);
        field(type,"runDelay").setInt(timer,200);
        type.getMethod("serverTick").invoke(timer);
        for(var key:WorldDimensions.EXPLORATION) {
            Object child=timers.get(key);
            check("goblin distinct timer "+key.location(),child!=timer && field(type,"level").get(child)==server.getLevel(key));
            check("goblin independent timer still ticks "+key.location(),field(type,"runDelay").getInt(child)==199);
        }
    }
    private void exercise(MinecraftServer server) throws Exception {
        server.setDifficulty(Difficulty.HARD,true);
        var home=server.overworld();
        var survival=server.getLevel(WorldDimensions.OVERWORLD);
        var adventure=server.getLevel(WorldDimensions.ADVENTURE);
        check("actual home identity",home.dimension().equals(Level.OVERWORLD));
        check("separate survival and adventure identities",survival!=null && adventure!=null && survival!=home && adventure!=home);
        List<ServerLevel> levels=List.of(home,survival,adventure,server.getLevel(Level.NETHER),server.getLevel(Level.END));
        Map<String,String> rules=new TreeMap<>();
        for(var level:levels) rules.put(level.dimension().location().toString(),level.getGameRules().createTag().toString());
        for(var level:levels) eventMatrix(level);
        // Native phantom/patrol/entity-type spawn paths execute inside the real NeoForge transformed runtime.
        for(var level:List.of(home,survival,adventure)) {
            player(server,level); nativePhantoms(level); nativePatrol(level); nativeTraders(level); nativeReinforcementPlacement(level);
        }
        var pet=EntityType.WOLF.create(home); pet.setTame(true,true); pet.moveTo(BASE,0,0);
        EventHooks.finalizeMobSpawn(pet,home,home.getCurrentDifficultyAt(BASE),MobSpawnType.MOB_SUMMONED,null);
        check("tamed pet can enter home",home.addFreshEntity(pet) && pet.isTame());
        // Demonstrate the exact NeoForge cancellation pitfall with an allowed summon control.
        cancelInitializationOnly=true;
        var golem=EntityType.IRON_GOLEM.create(home); golem.moveTo(BASE,0,0);
        EventHooks.finalizeMobSpawn(golem,home,home.getCurrentDifficultyAt(BASE),MobSpawnType.MOB_SUMMONED,null);
        cancelInitializationOnly=false;
        check("canceling initialization alone does not prevent insertion",!golem.isSpawnCancelled() && home.addFreshEntity(golem));
        goblinTimers(server);
        for(var level:levels) check("all gamerules unchanged "+level.dimension().location(),rules.get(level.dimension().location().toString()).equals(level.getGameRules().createTag().toString()));
        check("home ambient phantoms never join the level",joined.getOrDefault("minecraft:overworld/minecraft:phantom",0)==0);
        int phantomBefore=count(home,EntityType.PHANTOM);
        EntityType.PHANTOM.spawn(home,BASE,MobSpawnType.COMMAND);
        check("home explicit phantom summon still joins",count(home,EntityType.PHANTOM)==phantomBefore+1);
        observations.put("finalizeEvents",finalized); observations.put("joinEvents",joined);
    }
    private void tick(ServerTickEvent.Post event) {
        ticks++;
        if(ticks==20) {
            for(var key:List.of(Level.OVERWORLD,WorldDimensions.OVERWORLD,WorldDimensions.ADVENTURE,Level.NETHER,Level.END))
                pad(event.getServer().getLevel(key));
        }
        if(ticks!=40) return;
        Throwable error=null;
        try { exercise(event.getServer()); } catch(Throwable failure) { error=failure; failure.printStackTrace(); }
        try {
            var result=new LinkedHashMap<String,Object>(); result.put("success",error==null); result.put("passed",passed); result.put("observations",observations);
            if(error!=null) result.put("error",error.toString());
            Files.writeString(Path.of("home-spawning-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch(Exception failure) { failure.printStackTrace(); }
        event.getServer().halt(false);
    }
    private void stopped(ServerStoppedEvent event) {
        try { Files.writeString(Path.of("home-spawning-server-stopped"),"stopped\n"); }
        catch(Exception failure) { throw new RuntimeException(failure); }
    }
}
