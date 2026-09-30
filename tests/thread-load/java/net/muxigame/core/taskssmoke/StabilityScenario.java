package net.muxigame.core.taskssmoke;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Disposable stress workload, with observations from actual gameplay events. */
final class StabilityScenario {
    private static final int ARENA_X=96;
    static final boolean ENABLED=Boolean.getBoolean("muxi.qa.stability");
    private static final String[] NORMAL={"minecraft:zombie","minecraft:skeleton","minecraft:spider","minecraft:slime",
        "alexsmobs:grizzly_bear","alexsmobs:crocodile","mowziesmobs:foliaath","mowziesmobs:ferrous_wroughtnaut",
        "twilightforest:minotaur","twilightforest:skeleton_druid","iceandfire:troll","iceandfire:dread_knight",
        "cataclysm:ignited_revenant","cataclysm:deepling","illagerinvasion:invoker","crittersandcompanions:ferret"};
    private static final String[] BOSSES={"mowziesmobs:frostmaw","twilightforest:naga","iceandfire:cyclops","cataclysm:netherite_monstrosity"};
    private final Map<String,LongAdder> counters=new ConcurrentHashMap<>();
    private final Map<String,Integer> spawned=new TreeMap<>();
    private final List<String> missing=new ArrayList<>();
    private final List<Mob> active=new ArrayList<>();
    private volatile String phase="idle";
    private int waves;
    StabilityScenario(){
        if(!ENABLED)return;
        NeoForge.EVENT_BUS.addListener(this::damage);
        NeoForge.EVENT_BUS.addListener(this::death);
        NeoForge.EVENT_BUS.addListener(this::place);
        NeoForge.EVENT_BUS.addListener(this::breakBlock);
        NeoForge.EVENT_BUS.addListener(this::finalizeSpawn);
        NeoForge.EVENT_BUS.addListener(this::respawn);
    }
    private void count(String key){counters.computeIfAbsent(key,k->new LongAdder()).increment();}
    private boolean ours(Entity entity){return entity.getTags().contains("muxi_stress");}
    private void damage(LivingDamageEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer player&&phase.equals("survival-combat"))count("incomingPlayerDamage/"+player.getGameProfile().getName());
        if(!ours(event.getEntity()))return;
        count("damage/"+event.getEntity().level().dimension().location());
        count("damagePhase/"+phase);
        if(event.getSource().getEntity() instanceof ServerPlayer player)count("playerHits/"+player.getGameProfile().getName());
        else count("aiOrEnvironmentHits");
    }
    private void death(LivingDeathEvent event){
        if(ours(event.getEntity()))count("deaths");
        if(event.getEntity() instanceof ServerPlayer player&&phase.equals("survival-combat"))count("playerDeaths/"+player.getGameProfile().getName());
    }
    private void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event){
        if(!phase.equals("survival-combat")||!(event.getEntity() instanceof ServerPlayer player))return;
        count("playerRespawns/"+player.getGameProfile().getName());
        // The test's login marker is transient entity data; native death creates a new player entity.
        // Preserve fixture initialization so a later reconnect does not grant the initial items/XP twice.
        player.getPersistentData().putBoolean("load_initialized",true);
        var server=player.getServer();
        var level=player.getGameProfile().getName().startsWith("MuxiQAHome")?server.overworld():server.getLevel(WorldDimensions.OVERWORLD);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying=true;player.onUpdateAbilities();
        player.teleportTo(level,ARENA_X+.5,205,.5+lane(player)*Integer.getInteger("muxi.qa.spacing",2048),0,0);
    }
    private void place(BlockEvent.EntityPlaceEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&player.getGameProfile().getName().startsWith("MuxiQA"))count("placements/"+player.getGameProfile().getName());
    }
    private void breakBlock(BlockEvent.BreakEvent event){
        if(event.getPlayer() instanceof ServerPlayer player&&player.getGameProfile().getName().startsWith("MuxiQA"))count("breaks/"+player.getGameProfile().getName());
    }
    private void finalizeSpawn(FinalizeSpawnEvent event){
        count("spawnEvents/"+event.getSpawnType()+"/"+event.getLevel().getLevel().dimension().location());
    }
    private int lane(ServerPlayer player){String n=player.getGameProfile().getName().replaceAll("\\D","");return n.isEmpty()?0:Integer.parseInt(n)-1;}
    void begin(MinecraftServer server,int spacing){
        phase="stability";
        for(var player:server.getPlayerList().getPlayers()){
            var level=player.serverLevel();int z=lane(player)*spacing;
            for(int x=-12;x<=12;x++)for(int dz=-12;dz<=12;dz++)level.setBlockAndUpdate(new BlockPos(ARENA_X+x,198,z+dz),Blocks.STONE.defaultBlockState());
            player.teleportTo(level,ARENA_X+.5,199,.5+z,0,0);
            player.getInventory().setItem(1,new ItemStack(Items.COBBLESTONE,64));
            player.getInventory().setItem(2,new ItemStack(Items.DIAMOND_SWORD));
        }
        wave(server,spacing,false);
    }
    private void clear(){for(var mob:active)if(!mob.isRemoved())mob.discard();active.clear();}
    private Mob spawn(ServerLevel level,String id,double x,double y,double z){
        var key=ResourceLocation.parse(id);
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(key)){if(!missing.contains(id))missing.add(id);return null;}
        var entity=BuiltInRegistries.ENTITY_TYPE.get(key).create(level);
        if(!(entity instanceof Mob mob))throw new AssertionError("Expected Mob: "+id);
        mob.moveTo(x,y,z,0,0);mob.addTag("muxi_stress");mob.setPersistenceRequired();
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob,level,level.getCurrentDifficultyAt(mob.blockPosition()),MobSpawnType.COMMAND,null);
        if(!level.addFreshEntity(mob))throw new AssertionError("Rejected spawn: "+id);
        active.add(mob);spawned.merge(id,1,Integer::sum);return mob;
    }
    private void wave(MinecraftServer server,int spacing,boolean bosses){
        clear();waves++;
        for(var player:server.getPlayerList().getPlayers()){
            var level=player.serverLevel();double z=.5+lane(player)*spacing;
            var golem=spawn(level,"minecraft:iron_golem",ARENA_X+4.5,199,z);
            String[] ids=bosses?BOSSES:NORMAL;
            for(int i=0;i<ids.length;i++){
                var mob=spawn(level,ids[i],ARENA_X+(i%4)*3-5.5,199,z+(i/4)*3-5);
                if(mob!=null&&golem!=null){mob.setTarget(golem);if(golem instanceof IronGolem iron)iron.setTarget(mob);}
            }
            // A nearby ordinary target ensures native player attacks can be verified independently of boss AI.
            spawn(level,"minecraft:husk",ARENA_X+1.5,199,z+1);
        }
        System.out.println("STABILITY_WAVE "+waves+" bosses="+bosses+" entities="+active.size());
    }
    void tick(MinecraftServer server,int spacing,int tick){
        if(tick==400||tick==800)wave(server,spacing,false);
        if(tick==1200||tick==1800){phase="bosses";wave(server,spacing,true);}
        if(tick%100==0){
            // Keep AI fighting after the first target dies; otherwise long boss waves become idle tests.
            for(var player:server.getPlayerList().getPlayers()){
                var level=player.serverLevel();double z=.5+lane(player)*spacing;
                boolean hasTarget=active.stream().anyMatch(m->m instanceof IronGolem&&m.isAlive()&&m.level()==level&&Math.abs(m.getZ()-z)<32);
                if(!hasTarget){
                    var target=spawn(level,"minecraft:iron_golem",ARENA_X+4.5,199,z);
                    for(var mob:active)if(mob!=target&&mob.isAlive()&&mob.level()==level&&Math.abs(mob.getZ()-z)<32){mob.setTarget(target);target.setTarget(mob);}
                }
            }
            count("observations");
            for(var mob:active)if(mob.isAlive()&&mob.tickCount>20)count("liveTickObservations/"+BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
            assertNoSuppressedErrors();
            try{java.nio.file.Files.writeString(java.nio.file.Path.of("stability-live.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report()));}
            catch(java.io.IOException error){throw new RuntimeException(error);}
        }
    }
    void explore(MinecraftServer server,int spacing){
        phase="exploration";clear();
        for(var player:server.getPlayerList().getPlayers())player.teleportTo(player.serverLevel(),.5,336,.5+lane(player)*spacing,0,0);
    }
    void beginSurvivalCombat(MinecraftServer server,int spacing){
        phase="survival-combat";clear();
        for(var player:server.getPlayerList().getPlayers()){
            var level=player.serverLevel();double z=.5+lane(player)*spacing;
            // Keep inventory explicitly, so death/respawn can be checked without mixing in lost-item recovery.
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(true,server);
            for(int x=-3;x<=3;x++)for(int dz=-3;dz<=3;dz++)level.setBlockAndUpdate(new BlockPos(ARENA_X+x,198,(int)(z-.5)+dz),Blocks.STONE.defaultBlockState());
            player.teleportTo(level,ARENA_X+.5,199,z,0,0);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setHealth(6);
            for(int i=0;i<4;i++){
                var mob=spawn(level,"minecraft:husk",ARENA_X+.5+(i%2==0?1:-1),199,z+(i<2?1:-1));
                mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));mob.setTarget(player);
            }
        }
    }
    void validateSurvivalCombat(MinecraftServer server){
        for(var player:server.getPlayerList().getPlayers())for(String prefix:List.of("incomingPlayerDamage/","playerDeaths/","playerRespawns/")){
            String key=prefix+player.getGameProfile().getName();if(!counters.containsKey(key)||counters.get(key).sum()<1)throw new AssertionError("Missing survival combat evidence: "+key);
        }
    }
    static void assertNoSuppressedErrors(){
        try{
            var type=Class.forName("com.bawnorton.neruina.Neruina");
            var instance=type.getMethod("getInstance").invoke(null);
            var handler=type.getMethod("getTickHandler").invoke(instance);
            var entries=(Collection<?>)handler.getClass().getMethod("getTickingEntries").invoke(handler);
            if(!entries.isEmpty())throw new AssertionError("Neruina suppressed "+entries.size()+" ticking errors: "+entries);
        }catch(ClassNotFoundException absent){/* Minimal packs have no error suppression mod. */}
        catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    void validate(MinecraftServer server){
        assertNoSuppressedErrors();
        if(spawned.keySet().stream().map(id->id.split(":")[0]).distinct().count()<7)throw new AssertionError("Insufficient mod coverage: "+spawned);
        for(var player:server.getPlayerList().getPlayers())for(String prefix:List.of("playerHits/","placements/","breaks/")){
            String key=prefix+player.getGameProfile().getName();if(!counters.containsKey(key)||counters.get(key).sum()<5)throw new AssertionError("Missing gameplay evidence: "+key);
        }
        if(!counters.containsKey("deaths")||!counters.containsKey("aiOrEnvironmentHits"))throw new AssertionError("No observed mob combat/deaths");
    }
    void prepareSave(MinecraftServer server)throws java.io.IOException{
        var saved=new ArrayList<Map<String,String>>();
        for(var level:List.of(server.overworld(),server.getLevel(WorldDimensions.OVERWORLD))){
            int index=0;
            for(String id:List.of("minecraft:cow","alexsmobs:grizzly_bear","mowziesmobs:frostmaw","crittersandcompanions:ferret")){
                var mob=spawn(level,id,8.5+index++*3,199,8.5);
                if(mob==null)throw new AssertionError("Missing save-test mob "+id);
                mob.setNoAi(true);mob.setInvulnerable(true);
                saved.add(Map.of("dimension",level.dimension().location().toString(),"uuid",mob.getUUID().toString(),"type",id));
            }
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("stability-save-entities.json"),new com.google.gson.Gson().toJson(saved));
    }
    static void verifySave(MinecraftServer server)throws java.io.IOException{
        var rows=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("stability-save-entities.json"))).getAsJsonArray();
        if(rows.size()!=8)throw new AssertionError("Expected eight persisted entities");
        for(var row:rows){
            var obj=row.getAsJsonObject();
            var level=obj.get("dimension").getAsString().equals("minecraft:overworld")?server.overworld():server.getLevel(WorldDimensions.OVERWORLD);
            var entity=level.getEntity(UUID.fromString(obj.get("uuid").getAsString()));
            if(!(entity instanceof Mob mob)||!mob.isAlive()||!mob.isNoAi()||!mob.getTags().contains("muxi_stress")
                ||!BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString().equals(obj.get("type").getAsString()))
                throw new AssertionError("Persisted mob failed reload: "+obj);
        }
        assertNoSuppressedErrors();
    }
    Map<String,Object> report(){var counts=new TreeMap<String,Long>();counters.forEach((k,v)->counts.put(k,v.sum()));return Map.of("waves",waves,"spawned",spawned,"missingTypes",missing,"events",counts);}
}
