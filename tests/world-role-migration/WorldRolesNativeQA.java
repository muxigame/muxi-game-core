package net.muxigame.core.homespawnqa;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.muxigame.core.feature.spawning.*;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
@Mod("muxi_home_spawn_qa")
public final class WorldRolesNativeQA {
 public static volatile boolean sawSpawnerContext;
 private final List<String> checks=new ArrayList<>();private final Map<String,Integer> joined=new TreeMap<>();private int ticks,phase;private boolean done;private CompletableFuture<Boolean> npcFuture;private ExecutorService npcWorker;private int beforeNpc;
 static final BlockPos POS=new BlockPos(0,120,0);
 public WorldRolesNativeQA(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(EntityJoinLevelEvent e)->{if(!e.isCanceled()&&HomeSpawnContext.current()==HomeSpawnContext.Origin.SPAWNER)sawSpawnerContext=true;if(!e.isCanceled())joined.merge(e.getLevel().dimension().location()+"/"+BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()),1,Integer::sum);});}
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
 int count(ServerLevel l,EntityType<?> t){return joined.getOrDefault(l.dimension().location()+"/"+BuiltInRegistries.ENTITY_TYPE.getKey(t),0);}
 static Mob fresh(ServerLevel l,MobSpawnType cause){Mob m=EntityType.PIG.create(l);m.moveTo(POS,0,0);m.finalizeSpawn(l,l.getCurrentDifficultyAt(POS),cause,null);return m;}
 static WorldGenRegion region(ServerLevel level)throws Exception{Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);var region=(WorldGenRegion)unsafe.allocateInstance(WorldGenRegion.class);for(Field field:WorldGenRegion.class.getDeclaredFields())if(field.getType()==ServerLevel.class){field.setAccessible(true);field.set(region,level);}return region;}
 @SuppressWarnings({"unchecked","rawtypes"})
 void modCallChains(ServerLevel home,ServerLevel resource)throws Exception{
  Class<?> seaClass=Class.forName("com.iafenvoy.iceandfire.world.feature.SeaSerpentSpawnFeature");
  var none=net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE;
  var sea=(net.minecraft.world.level.levelgen.feature.Feature)seaClass.getConstructor(com.mojang.serialization.Codec.class).newInstance(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC);
  EntityType<?> seaType=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("iceandfire:sea_serpent"));
  for(ServerLevel level:List.of(home,resource)){
   java.util.concurrent.atomic.AtomicInteger featureInsertCalls=new java.util.concurrent.atomic.AtomicInteger();
   var distantSpawn=(net.minecraft.world.level.storage.LevelData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{net.minecraft.world.level.storage.LevelData.class},(proxy,m,args)->m.getName().equals("getSpawnPos")?new BlockPos(-100000,120,-100000):m.invoke(level.getLevelData(),args));
   var gen=(WorldGenLevel)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,m,args)->switch(m.getName()){
    case "getHeightmapPos"->POS.offset(4,0,4);case "getFluidState"->Blocks.WATER.defaultBlockState().getFluidState();case "getLevel"->level;case "getLevelData"->distantSpawn;
    case "addFreshEntity"->{featureInsertCalls.incrementAndGet();Entity entity=(Entity)args[0];if(entity instanceof Mob mob)mob.setNoAi(true);yield level.addFreshEntity(entity);}
    default->throw new UnsupportedOperationException("Unexpected sea feature environment method "+m.getName());});
   RandomSource seeded=RandomSource.create(37);
   var chance=(RandomSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RandomSource.class},(proxy,m,args)->{if(m.getName().equals("nextDouble"))return 0d;if(m.getName().equals("nextInt")&&args!=null&&args.length==1)return (Integer)args[0]==10?5:0;return m.invoke(seeded,args);});
   int before=count(level,seaType);var context=new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext(Optional.empty(),gen,level.getChunkSource().getGenerator(),chance,POS,none);sea.place(context);
   check(featureInsertCalls.get()==1,"actual Ice sea-serpent feature reached its unfinalized insertion "+level.dimension().location());
   check(count(level,seaType)==before+(level==home?0:1),"actual Ice sea-serpent feature home rejected and resource retained "+level.dimension().location());
  }
  Class<?> biomeConfig=Class.forName("com.github.alexthe666.alexsmobs.config.BiomeConfig");Field values=biomeConfig.getDeclaredField("biomeConfigValues");values.setAccessible(true);Map biomeValues=(Map)values.get(null);
  Object pair=biomeConfig.getField("cachalot_whale_beached_spawns").get(null);String key=(String)pair.getClass().getMethod("getLeft").invoke(pair);Object old=biomeValues.get(key);
  Class<?> dataType=Class.forName("com.github.alexthe666.citadel.config.biome.SpawnBiomeData"),entryType=Class.forName("com.github.alexthe666.citadel.config.biome.BiomeEntryType");Object data=dataType.getConstructor().newInstance();Object registryEntry=entryType.getField("REGISTRY_NAME").get(null);
  try{
   for(ServerLevel level:List.of(home,resource)){
    for(int x=14;x<=15;x++)for(int z=14;z<=15;z++){for(int y=201;y<level.getMaxBuildHeight();y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);level.setBlock(new BlockPos(x,200,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);}
    String biome=level.getBiome(new BlockPos(14,201,14)).unwrapKey().orElseThrow().location().toString();dataType.getMethod("addBiomeEntry",entryType,boolean.class,String.class,int.class).invoke(data,registryEntry,false,biome,level==home?0:1);
   }
   biomeValues.put(key,data);
   Class<?> spawnerType=Class.forName("com.github.alexthe666.alexsmobs.world.BeachedCachalotWhaleSpawner");Field random=spawnerType.getDeclaredField("random");random.setAccessible(true);Method attempt=spawnerType.getDeclaredMethod("attemptSpawnWhale");attempt.setAccessible(true);
   EntityType<?> whale=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("alexsmobs:cachalot_whale"));
   for(ServerLevel level:List.of(home,resource)){
    if(level==resource){var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WhaleQA"));actor.moveTo(POS,0,0);level.addNewPlayer(actor);}
    Object spawner=spawnerType.getConstructor(ServerLevel.class).newInstance(level);random.set(spawner,new java.util.Random(17){@Override public int nextInt(int bound){return bound==168?98:0;}});
    var player=level.getRandomPlayer();check(player!=null,"actual Alex fixture has a nearby world player "+level.dimension().location());
    Method find=spawnerType.getDeclaredMethod("func_221244_a",BlockPos.class,int.class);find.setAccessible(true);BlockPos beach=(BlockPos)find.invoke(spawner,player.blockPosition(),84);
    Field initialized=biomeConfig.getDeclaredField("init");initialized.setAccessible(true);
    check(beach!=null,"Alex fixture finds biome-valid beach; init="+initialized.get(null)+", actor="+player.blockPosition()+", targetBiome="+level.getBiome(new BlockPos(14,201,14)).unwrapKey());
    Method clear=spawnerType.getDeclaredMethod("func_226559_a_",BlockPos.class);clear.setAccessible(true);check((Boolean)clear.invoke(spawner,beach),"Alex fixture beach clear "+beach+" block="+level.getBlockState(beach));check(beach.distSqr(player.blockPosition())>225,"Alex fixture beach has required player distance");
    int before=count(level,whale);check((Boolean)attempt.invoke(spawner),"actual Alex beached-whale routine reached its insertion "+level.dimension().location());
    check(count(level,whale)==before+(level==home?0:1),"actual Alex beached-whale fake SPAWNER home rejected and resource retained "+level.dimension().location());
   }
  }finally{if(old==null)biomeValues.remove(key);else biomeValues.put(key,old);}
 }
 void matrix(MinecraftServer server)throws Exception{
  ServerLevel home=server.getLevel(WorldDimensions.HOME),resource=server.overworld(),adventure=server.getLevel(WorldDimensions.ADVENTURE);
  check(resource!=null&&adventure!=null,"all three real dimensions registered");check(HomeSpawningFeature.protectedHome(home),"dedicated Core guard registered");check(home.dimension().location().toString().equals("muxi_game_core:homeworld"),"actual migrated homeworld ID");if(Boolean.getBoolean("qa.worldRoles.migratedFixture")){check(home.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource,"cold migration retains original flat home generator");check(resource.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator,"cold migration retains original noise survival generator");var role=net.muxigame.core.feature.dimensions.WorldRoleState.get(server);check(role.homeHub(),"cold migration retains home portal and fallback policy");check(role.homeSpawn()!=null&&home.getSharedSpawnPos().equals(role.homeSpawn()),"cold migration retains original independent home spawn");check(resource.getSharedSpawnPos().equals(POS),"cold migration keeps selected primary fixture spawn");}check(resource.dimension().equals(Level.OVERWORLD)&&!HomeSpawningFeature.protectedHome(resource),"actual primary overworld is survival and not forbidden");check(server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("muxi_game_core:overworld")))==null,"legacy survival ID is not registered");
  for(ServerLevel level:List.of(home,resource,adventure)){
   level.setChunkForced(0,0,true);level.getChunk(0,0);for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlock(POS.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(MobSpawnType cause:MobSpawnType.values()){
    Mob m=fresh(level,cause);check(m instanceof FreshMobSpawn,"fresh cause mixin present "+cause);check(((FreshMobSpawn)m).muxi$freshSpawnReason()==cause,"actual finalize provenance "+cause);
    boolean expected=level!=home||Set.of("BREEDING","CONVERSION","BUCKET","SPAWN_EGG","DISPENSER").contains(cause.name());int before=count(level,m.getType());boolean added=level.addFreshEntity(m);check(added==expected,"native admission "+level.dimension().location()+"/"+cause);check(count(level,m.getType())-before==(expected?1:0),"native entity join count "+level.dimension().location()+"/"+cause);
   }
  }
  for(HomeSpawnContext.Origin origin:List.of(HomeSpawnContext.Origin.PLAYER,HomeSpawnContext.Origin.SPAWNER))try(var scope=HomeSpawnContext.enter(origin)){Mob m=EntityType.PIG.create(home);m.moveTo(POS,0,0);check(home.addFreshEntity(m),"unfinalized deliberate insertion "+origin);}
  check(HomeSpawnContext.current()==null,"scope cleanup before ambient insertion");check(!home.addFreshEntity(EntityType.PIG.create(home)),"unfinalized unknown fresh mob denied");
  CompoundTag old=new CompoundTag();old.putString("neoforge:spawn_type","COMMAND");Mob clone=EntityType.PIG.create(home);clone.readAdditionalSaveData(old);check(clone.getSpawnType()==MobSpawnType.COMMAND,"old NBT cause restored");check(((FreshMobSpawn)clone).muxi$freshSpawnReason()==null,"old NBT is not fresh proof");check(!home.addFreshEntity(clone),"clone NBT cannot impersonate a command");
  Mob carry=EntityType.WOLF.create(home);carry.readAdditionalSaveData(old);UUID id=carry.getUUID();float hp=carry.getHealth();carry.moveTo(POS,0,0);int carryBefore=count(home,carry.getType());home.addDuringTeleport(carry);Field carryManagerField=ServerLevel.class.getDeclaredField("entityManager");carryManagerField.setAccessible(true);Object carryManager=carryManagerField.get(home);boolean carryRegistered=(Boolean)carryManager.getClass().getMethod("isLoaded",UUID.class).invoke(carryManager,id);check(carryRegistered&&count(home,carry.getType())==carryBefore+1&&carry.getUUID().equals(id)&&carry.getHealth()==hp,"real target teleport insertion retains registered creature UUID and HP");
  var loaded=EntityType.WOLF.create(home);loaded.readAdditionalSaveData(old);Field managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);Object manager=managerField.get(home);Method legacy=manager.getClass().getMethod("addLegacyChunkEntities",java.util.stream.Stream.class);int before=count(home,loaded.getType());legacy.invoke(manager,java.util.stream.Stream.of(loaded));check(count(home,loaded.getType())==before+1,"actual disk entity loading retained");
  Mob serpent=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("iceandfire:sea_serpent")).create(home);check(serpent!=null,"actual Ice and Fire entity registered");check(!region(home).addFreshEntity(serpent),"actual worldgen region blocks direct Ice and Fire fresh mob");
  Mob whale=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("alexsmobs:cachalot_whale")).create(home);check(whale!=null,"actual Alex whale registered");whale.finalizeSpawn(home,home.getCurrentDifficultyAt(POS),MobSpawnType.SPAWNER,null);check(!home.addFreshEntity(whale),"actual Alex whale fake SPAWNER denied without cage context");
  try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.SPAWNER)){whale=(Mob)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("alexsmobs:cachalot_whale")).create(home);whale.finalizeSpawn(home,home.getCurrentDifficultyAt(POS),MobSpawnType.SPAWNER,null);check(home.addFreshEntity(whale),"Alex whale retains genuine spawner provenance");}
  Method lightningAdapter=Arrays.stream(ServerLevel.class.getDeclaredMethods()).filter(m->m.getName().contains("muxi$lightning")&&m.getParameterCount()==3).findFirst().orElseThrow();lightningAdapter.setAccessible(true);
  java.util.concurrent.atomic.AtomicInteger originalFactoryCalls=new java.util.concurrent.atomic.AtomicInteger();com.llamalad7.mixinextras.injector.wrapoperation.Operation<Entity> factory=args->{originalFactoryCalls.incrementAndGet();return ((EntityType<?>)args[0]).create((Level)args[1]);};
  check(lightningAdapter.invoke(home,EntityType.SKELETON_HORSE,home,factory)==null&&originalFactoryCalls.get()==0,"actual transformed lightning branch suppresses only home trap-horse factory");
  check(lightningAdapter.invoke(home,EntityType.LIGHTNING_BOLT,home,factory)!=null&&originalFactoryCalls.get()==1,"actual transformed lightning branch keeps real lightning factory");
  check(lightningAdapter.invoke(resource,EntityType.SKELETON_HORSE,resource,factory)!=null&&originalFactoryCalls.get()==2,"resource lightning trap factory retained");
  SkeletonHorse trap=EntityType.SKELETON_HORSE.create(home);trap.setTrap(true);check(!home.addFreshEntity(trap),"unfinalized lightning trap birth denied");
  try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){trap=EntityType.SKELETON_HORSE.create(home);trap.setTrap(true);trap.moveTo(POS,0,0);check(home.addFreshEntity(trap),"player summoned horse retained");check(!trap.isTrap(),"player summoned trap flag disarmed in home");}
  SkeletonHorse existing=EntityType.SKELETON_HORSE.create(home);existing.setTrap(true);int hb=count(home,existing.getType());legacy.invoke(manager,java.util.stream.Stream.of(existing));check(count(home,existing.getType())==hb+1&&!existing.isTrap(),"existing saved trap horse retained and disarmed");existing.setTrap(true);existing.aiStep();check(!existing.isRemoved()&&!existing.isTrap(),"already alive trap disarmed without deletion");
  int horses=count(home,EntityType.SKELETON_HORSE),riders=count(home,EntityType.SKELETON);Method trapTask=SkeletonTrapGoal.class.getDeclaredMethod("lambda$tick$0",ServerLevel.class);trapTask.setAccessible(true);new SkeletonTrapGoal(existing).tick();trapTask.invoke(new SkeletonTrapGoal(existing),home);check(count(home,EntityType.SKELETON_HORSE)==horses&&count(home,EntityType.SKELETON)==riders,"direct trap goal cannot create extra horses or riders");
  SkeletonHorse outside=EntityType.SKELETON_HORSE.create(resource);outside.setTrap(true);resource.addFreshEntity(outside);check(outside.isTrap(),"resource trap state retained");int oh=count(resource,EntityType.SKELETON_HORSE),or=count(resource,EntityType.SKELETON);trapTask.invoke(new SkeletonTrapGoal(outside),resource);check(count(resource,EntityType.SKELETON_HORSE)==oh+3&&count(resource,EntityType.SKELETON)==or+4,"actual resource trap goal retains extra three horses and four riders");check(!new SkeletonTrapGoal(existing).canUse(),"home trap goal never eligible");
  var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(home,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CageQA"));fake.moveTo(POS,0,0);home.addNewPlayer(fake);
  var cage=new BaseSpawner(){public void broadcastEvent(Level level,BlockPos pos,int type){}};
  var cageTag=new CompoundTag();cageTag.putShort("Delay",(short)0);cageTag.putShort("MinSpawnDelay",(short)1);cageTag.putShort("MaxSpawnDelay",(short)2);cageTag.putShort("SpawnCount",(short)1);cageTag.putShort("MaxNearbyEntities",(short)128);cageTag.putShort("RequiredPlayerRange",(short)32);cageTag.putShort("SpawnRange",(short)0);
  var cageData=new CompoundTag();var pigTag=new CompoundTag();pigTag.putString("id","minecraft:pig");var position=new ListTag();position.add(DoubleTag.valueOf(8.5));position.add(DoubleTag.valueOf(120));position.add(DoubleTag.valueOf(8.5));pigTag.put("Pos",position);cageData.put("entity",pigTag);cageTag.put("SpawnData",cageData);cage.load(home,POS,cageTag);
  home.setBlock(new BlockPos(8,119,8),Blocks.GRASS_BLOCK.defaultBlockState(),2);
  int cb=count(home,EntityType.PIG);for(int i=0;i<5&&count(home,EntityType.PIG)==cb;i++)cage.serverTick(home,POS);
  check(count(home,EntityType.PIG)>cb&&sawSpawnerContext,"real vanilla BaseSpawner tick creates an allowed mob in actual cage scope");check(HomeSpawnContext.current()==null,"real cage scope cleared");
  var parentPig=EntityType.PIG.create(home);var matePig=EntityType.PIG.create(home);parentPig.moveTo(POS.offset(10,0,0),0,0);matePig.moveTo(POS.offset(10,0,0),0,0);
  int babies=count(home,EntityType.PIG);parentPig.spawnChildFromBreeding(home,matePig);
  check(count(home,EntityType.PIG)==babies+1,"actual Animal breeding creates a retained home baby");check(HomeSpawnContext.current()==null,"actual breeding scope cleaned");
  // Exact production CustomNPC automatic resurrection must not turn a dead home NPC alive.
  noppes.npcs.entity.EntityNPCInterface homeNpc=null,otherNpc=null;
  for(var type:BuiltInRegistries.ENTITY_TYPE){if(!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("customnpcs"))continue;Entity entity=type.create(home);if(entity instanceof noppes.npcs.entity.EntityNPCInterface npc){homeNpc=npc;otherNpc=(noppes.npcs.entity.EntityNPCInterface)type.create(resource);break;}}
  check(homeNpc!=null&&otherNpc!=null,"actual production CustomNPC registered");
  homeNpc.stats.spawnCycle=0;homeNpc.hasDied=true;homeNpc.killedtime=0;homeNpc.setHealth(0);UUID npcId=homeNpc.getUUID();homeNpc.tickDeath();
  check(homeNpc.hasDied&&homeNpc.getHealth()==0&&homeNpc.getUUID().equals(npcId),"actual CustomNPC home automatic death reset blocked without entity replacement");
  otherNpc.stats.spawnCycle=0;otherNpc.hasDied=true;otherNpc.killedtime=0;otherNpc.setHealth(0);otherNpc.tickDeath();
  check(!otherNpc.hasDied&&otherNpc.getHealth()>0,"actual CustomNPC resource automatic reset retained");
  homeNpc.reset();check(!homeNpc.hasDied&&homeNpc.getHealth()>0,"actual CustomNPC explicit reset retained");
  // EasyNPC's original method discards its old entity before adding a replacement; veto must precede that discard.
  Class<?> easyType=Class.forName("de.markusbordihn.easynpc.entity.easynpc.EasyNPC");Entity easyEntity=null;
  for(var type:BuiltInRegistries.ENTITY_TYPE){if(!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("easy_npc"))continue;Entity entity=type.create(home);if(easyType.isInstance(entity)){easyEntity=entity;break;}}
  check(easyEntity!=null,"actual EasyNPC registered");
  Method respawn=Class.forName("de.markusbordihn.easynpc.handler.RespawnHandler").getDeclaredMethod("respawnNPC",easyType,ServerLevel.class);
  UUID easyId=easyEntity.getUUID();float easyHp=((LivingEntity)easyEntity).getHealth();
  check(!(Boolean)respawn.invoke(null,easyEntity,home)&&!easyEntity.isRemoved()&&easyEntity.getUUID().equals(easyId)&&((LivingEntity)easyEntity).getHealth()==easyHp,"actual EasyNPC ambient replacement rejected before old NPC discard");
  modCallChains(home,resource);
  // Original command permission path remains in force; no game/client input simulation.
  int pb=count(home,EntityType.PIG);server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(home).withPermission(4),"summon minecraft:pig 0 120 0 {NoAI:1b}");check(count(home,EntityType.PIG)==pb+1,"real permitted summon command retained");check(HomeSpawnContext.current()==null,"command scope cleaned");
  server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(fake.getGameProfile(),4,true));
  var tool=new noppes.npcs.packets.server.SPacketToolMobSpawner(false,POS,pigTag.copy());Field actor=noppes.npcs.packets.PacketServerBasic.class.getDeclaredField("player");actor.setAccessible(true);actor.set(tool,fake);
  check(noppes.npcs.CustomNpcsPermissions.hasPermission(fake,tool.getPermission()),"NPC tool fixture has original mod permission");
  int tb=count(home,EntityType.PIG);Method toolHandle=tool.getClass().getDeclaredMethod("handle");toolHandle.setAccessible(true);toolHandle.invoke(tool);check(count(home,EntityType.PIG)==tb+1,"actual permitted NPC player tool creates its clone");check(HomeSpawnContext.current()==null,"NPC player tool context cleaned");
  // Invoke the exact production NPC spawn routine from a foreign thread. It creates a clone with stale COMMAND NBT then submits the actual add task.
  home.setBlock(POS.offset(12,-1,12),Blocks.GRASS_BLOCK.defaultBlockState(),2);
  beforeNpc=count(home,EntityType.PIG);npcWorker=Executors.newSingleThreadExecutor();npcFuture=CompletableFuture.supplyAsync(()->{try{Class<?> npc=Class.forName("noppes.npcs.NPCSpawning");Method spawn=npc.getDeclaredMethod("spawnData",noppes.npcs.controllers.data.SpawnData.class,ServerLevelAccessor.class,BlockPos.class);spawn.setAccessible(true);try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){return (Boolean)spawn.invoke(null,new FixtureSpawnData(),home,POS.offset(12,0,12));}}catch(Exception e){throw new CompletionException(e);}},npcWorker);
 }
 public static final class FixtureSpawnData extends noppes.npcs.controllers.data.SpawnData {public CompoundTag getCompound(int ignored){var tag=new CompoundTag();tag.putString("id","minecraft:pig");tag.putString("neoforge:spawn_type","COMMAND");return tag;}}
 void tick(ServerTickEvent.Post event){if(done)return;try{ticks++;if(phase==0&&ticks>=40){matrix(event.getServer());phase=1;}else if(phase==1&&npcFuture.isDone()&&ticks>=80){check(npcFuture.join(),"actual NPC routine queued an asynchronous spawn");check(count(event.getServer().getLevel(WorldDimensions.HOME),EntityType.PIG)==beforeNpc,"actual NPC asynchronous stale COMMAND clone rejected");check(HomeSpawnContext.current()==null,"NPC server task context cleaned");try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){check(event.getServer().getLevel(WorldDimensions.HOME).addFreshEntity(EntityType.PIG.create(event.getServer().getLevel(WorldDimensions.HOME))),"later player tool insertion not poisoned by NPC task");}npcWorker.shutdown();finish(true,"");}}catch(Throwable t){if(npcWorker!=null)npcWorker.shutdown();try{finish(false,t.toString());}catch(Exception io){io.printStackTrace();}t.printStackTrace();}}
 void finish(boolean ok,String error)throws Exception{done=true;var report=new LinkedHashMap<String,Object>();report.put("success",ok);report.put("error",error);report.put("checks",checks);report.put("joined",joined);report.put("productionMutation",false);report.put("minecraftClients",0);Files.writeString(Path.of("home-spawn-native-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));String lab=System.getProperty("qa.local.root"),run=System.getProperty("qa.local.runId");if(lab!=null&&run!=null)Files.writeString(Path.of(lab,"stop-request.json"),new Gson().toJson(Map.of("runId",run,"normalStopOnly",true,"requestedBy","home-spawn-qa-finished")));}
}
