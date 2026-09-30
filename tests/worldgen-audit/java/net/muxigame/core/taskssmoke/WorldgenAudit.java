package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.GameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Read-only registry audit and bounded generation in a fresh, disposable world. */
@Mod("muxi_tasks_smoke")
public final class WorldgenAudit {
    private boolean done;
    private final List<String> failures=new ArrayList<>();
    public WorldgenAudit() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{
            try {Files.writeString(Path.of("dimensions-server-stopped"),"stopped");}catch(Exception x){throw new RuntimeException(x);}
        });
    }
    private Map<String,Object> inspect(ServerLevel level) {
        var result=new LinkedHashMap<String,Object>();
        var biomes=level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        var biomeIds=new TreeSet<String>();var spawnIds=new TreeSet<String>();
        for(var holder:biomes) {
            biomeIds.add(holder.unwrapKey().orElseThrow().location().toString());
            for(var category:MobCategory.values())for(var spawn:holder.value().getMobSettings().getMobs(category).unwrap())
                spawnIds.add(BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type).toString());
        }
        result.put("biomes",biomeIds);result.put("biomeSpawnCandidates",spawnIds);
        var structures=new TreeSet<String>();
        for(var set:level.getChunkSource().getGeneratorState().possibleStructureSets())for(var entry:set.value().structures())
            if(entry.structure().value().biomes().stream().anyMatch(biomes::contains))structures.add(entry.structure().unwrapKey().orElseThrow().location().toString());
        result.put("structureCandidates",structures);
        return result;
    }
    private void check(String description,boolean valid) {if(!valid)failures.add(description);}
    private Map<String,String> starts(ServerLevel level,int x,int z) {
        var entries=new TreeMap<String,String>();
        for(var entry:level.getChunk(x,z,ChunkStatus.STRUCTURE_STARTS).getAllStarts().entrySet())if(entry.getValue().isValid())
            entries.put(level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey()).toString(),
                entry.getValue().createTag(StructurePieceSerializationContext.fromLevel(level),new net.minecraft.world.level.ChunkPos(x,z)).toString());
        return entries;
    }
    private Map<String,Object> blocks(ServerLevel a,ServerLevel b,int x,int z) throws Exception {
        var first=a.getChunk(x,z);var second=b.getChunk(x,z);
        var hashA=MessageDigest.getInstance("SHA-256");var hashB=MessageDigest.getInstance("SHA-256");
        long differences=0;var examples=new ArrayList<String>();
        var pos=new BlockPos.MutableBlockPos();
        for(int y=a.getMinBuildHeight();y<a.getMaxBuildHeight();y++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++) {
            pos.set(x*16+dx,y,z*16+dz);
            var sa=first.getBlockState(pos);var sb=second.getBlockState(pos);
            hashA.update((sa.toString()+"\n").getBytes(StandardCharsets.UTF_8));hashB.update((sb.toString()+"\n").getBytes(StandardCharsets.UTF_8));
            if(!sa.equals(sb)){differences++;if(examples.size()<8)examples.add(pos+": "+sa+" / "+sb);}
        }
        check("block equality at "+x+","+z,differences==0);
        return Map.of("chunk",List.of(x,z),"differentBlocks",differences,"examples",examples,
            "homeHash",HexFormat.of().formatHex(hashA.digest()),"survivalHash",HexFormat.of().formatHex(hashB.digest()));
    }
    private void progress(String message) {
        System.out.println("WORLDGEN_AB: "+message);
        try{Files.writeString(Path.of("audit-progress.txt"),message);}catch(Exception ignored){}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private List<Object> spawnEligibility(ServerLevel a,ServerLevel b) throws Exception {
        var reports=new ArrayList<Object>();
        var configs=(Map<?,?>)Class.forName("com.bobmowzie.mowziesmobs.server.world.spawn.SpawnHandler").getField("SPAWN_CONFIGS").get(null);
        var predicate=Class.forName("com.bobmowzie.mowziesmobs.server.entity.MowzieEntity").getMethod("spawnPredicate",net.minecraft.world.entity.EntityType.class,net.minecraft.world.level.LevelAccessor.class,net.minecraft.world.entity.MobSpawnType.class,BlockPos.class,net.minecraft.util.RandomSource.class);
        BlockPos pos=new BlockPos(4097,200,4097);
        for(ServerLevel level:List.of(a,b)){level.setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}
        for(var entry:configs.entrySet()) {
            var entity=(net.minecraft.world.entity.EntityType<?>)entry.getKey();Object config=entry.getValue();
            var changes=new LinkedHashMap<net.neoforged.neoforge.common.ModConfigSpec.ConfigValue,Object>();
            try {
                // Isolate the dimension gate. Real biome weights/blocks/heights stay unchanged in the mod;
                // this disposable fixture neutralizes other predicates and restores every value below.
                var neutral=Map.of("extraRarity",1.0,"heightMax",-65,"heightMin",-65,"needsDarkness",false,"needsSeeSky",false,"needsCantSeeSky",false,"allowedBlocks",List.of("minecraft:stone"),"allowedBlockTags",List.of(),"avoidStructures",List.of());
                for(var setting:neutral.entrySet()) {
                    var value=(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue)config.getClass().getField(setting.getKey()).get(config);
                    changes.put(value,value.get());value.set(setting.getValue());
                }
                boolean home=(boolean)predicate.invoke(null,entity,a,net.minecraft.world.entity.MobSpawnType.NATURAL,pos,net.minecraft.util.RandomSource.create(7));
                boolean survival=(boolean)predicate.invoke(null,entity,b,net.minecraft.world.entity.MobSpawnType.NATURAL,pos,net.minecraft.util.RandomSource.create(7));
                String id=BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString();
                check("Mowzie native natural dimension predicate "+id,home&&survival);
                reports.add(Map.of("entity",id,"home",home,"survival",survival));
            } finally {for(var change:changes.entrySet())change.getKey().set(change.getValue());}
        }
        return reports;
    }
    @SuppressWarnings("unchecked")
    private void tick(ServerTickEvent.Post event) {
        if(done)return;done=true;
        var server=event.getServer();var output=new LinkedHashMap<String,Object>();
        output.put("threading",net.muxigame.core.threading.DimensionThreads.metrics(server));
        output.put("loadedC2meModules",net.neoforged.fml.ModList.get().getMods().stream()
            .map(net.neoforged.neoforgespi.language.IModInfo::getModId).filter(id->id.startsWith("c2me")).sorted().toList());
        try {
            var a=server.overworld();var b=server.getLevel(WorldDimensions.OVERWORLD);
            a.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
            a.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            output.put("seed",a.getSeed());check("same seed",a.getSeed()==b.getSeed());
            check("distinct world identities",!a.dimension().equals(b.dimension()));
            var home=inspect(a);var remote=inspect(b);output.put("home",home);output.put("survival",remote);
            for(String key:List.of("biomes","biomeSpawnCandidates","structureCandidates")) {
                var missing=new TreeSet<>((Set<String>)home.get(key));missing.removeAll((Set<String>)remote.get(key));output.put("missing_"+key,missing);
                var extra=new TreeSet<>((Set<String>)remote.get(key));extra.removeAll((Set<String>)home.get(key));output.put("extra_"+key,extra);
                check(key+" sets match",missing.isEmpty()&&extra.isEmpty());
            }
            progress("registry comparison completed");
            var sourceA=a.getChunkSource().getGenerator().getBiomeSource();var sourceB=b.getChunkSource().getGenerator().getBiomeSource();
            int biomeDiff=0,samples=0;var biomeExamples=new ArrayList<String>();var environmental=new TreeMap<String,int[]>();
            for(int x=-16384;x<=16384;x+=512)for(int z=-16384;z<=16384;z+=512)for(int y:new int[]{-32,64,160}) {
                var ba=sourceA.getNoiseBiome(x>>2,y>>2,z>>2,a.getChunkSource().randomState().sampler());
                var bb=sourceB.getNoiseBiome(x>>2,y>>2,z>>2,b.getChunkSource().randomState().sampler());samples++;
                String biomeId=ba.unwrapKey().orElseThrow().location().toString();
                if(y==64&&biomeId.startsWith("environmental:"))environmental.putIfAbsent(biomeId,new int[]{x>>4,z>>4});
                if(!ba.equals(bb)){biomeDiff++;if(biomeExamples.size()<10)biomeExamples.add(x+","+y+","+z+": "+ba.unwrapKey()+" / "+bb.unwrapKey());}
            }
            output.put("biomeSamples",samples);output.put("biomeDifferences",biomeDiff);output.put("biomeExamples",biomeExamples);check("biome coordinates match",biomeDiff==0);
            progress("biome coordinates compared: "+biomeDiff+" differences");
            var chunkReports=new ArrayList<Map<String,Object>>();
            for(int[] point:new int[][]{{256,256},{-256,256},{256,-256},{-256,-256}}) {
                progress("full chunk comparison "+Arrays.toString(point));chunkReports.add(blocks(a,b,point[0],point[1]));
            }
            output.put("environmentalBiomeLocations",environmental);
            for(var entry:environmental.entrySet()) {
                progress("Environmental full chunk comparison "+entry.getKey());
                chunkReports.add(blocks(a,b,entry.getValue()[0],entry.getValue()[1]));
            }
            output.put("chunkComparisons",chunkReports);
            var structureReports=new ArrayList<Object>();var namespaces=new HashSet<String>();
            for(var set:a.getChunkSource().getGeneratorState().possibleStructureSets()) {
                String id=set.unwrapKey().orElseThrow().location().toString(),namespace=id.split(":")[0];
                if(namespace.equals("minecraft")||!namespaces.add(namespace)||!(set.value().placement() instanceof RandomSpreadStructurePlacement spread))continue;
                for(int region=4;region<=6;region++) {
                    var point=spread.getPotentialStructureChunk(a.getSeed(),region*spread.spacing(),region*spread.spacing());
                    var first=starts(a,point.x,point.z);var second=starts(b,point.x,point.z);
                    check("structure starts "+point,first.equals(second));
                    structureReports.add(Map.of("set",id,"chunk",List.of(point.x,point.z),"home",first,"survival",second,"equal",first.equals(second)));
                }
            }
            output.put("structureComparisons",structureReports);progress("structure candidate chunks compared");
            Object seasons=Class.forName("sereneseasons.init.ModConfig").getField("seasons").get(null);
            var allowed=seasons.getClass().getMethod("isDimensionWhitelisted",net.minecraft.resources.ResourceKey.class);
            boolean seasonA=(boolean)allowed.invoke(seasons,a.dimension()),seasonB=(boolean)allowed.invoke(seasons,b.dimension());
            output.put("seasonWhitelist",List.of(seasonA,seasonB));check("season whitelist matches and enabled",seasonA&&seasonB);
            output.put("mowzieIsolatedDimensionPredicates",spawnEligibility(a,b));
            var goblinClass=Class.forName("com.mrcrayfish.goblintraders.spawner.GoblinTraderSpawner");
            var getGoblin=goblinClass.getMethod("get",net.minecraft.server.MinecraftServer.class,net.minecraft.world.entity.EntityType.class);
            Object homeTimer=((Optional<?>)getGoblin.invoke(null,server,BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("goblintraders:goblin_trader")))) .orElseThrow();
            var extraTimerField=Arrays.stream(goblinClass.getDeclaredFields()).filter(f->f.getName().endsWith("muxi$survivalSpawner")).findFirst().orElseThrow();extraTimerField.setAccessible(true);
            Object survivalTimer=extraTimerField.get(homeTimer);check("survival trader timer exists",survivalTimer!=null&&survivalTimer!=homeTimer);
            var timerLevel=goblinClass.getDeclaredField("level");timerLevel.setAccessible(true);
            check("trader timer belongs to survival",timerLevel.get(survivalTimer)==b);
            output.put("independentSurvivalGoblinTimer",survivalTimer!=null&&timerLevel.get(survivalTimer)==b);
            output.put("failures",failures);output.put("success",failures.isEmpty());
            output.put("passed",List.of("A/B comparisons executed; see failures and detailed hashes"));
            output.put("scope","Same process and seed, shared public gameplay configs/Paxi/KubeJS. Bounded generated blocks/biomes/structure starts; no claim of exhaustive infinite terrain or identical dynamic entity UUIDs/timing.");
        }catch(Throwable error){error.printStackTrace();output.put("success",false);output.put("error",error.toString());output.put("failures",failures);}
        output.put("threading",net.muxigame.core.threading.DimensionThreads.metrics(server));
        try {Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(output));}catch(Exception e){throw new RuntimeException(e);}
        server.halt(false);
    }
}
