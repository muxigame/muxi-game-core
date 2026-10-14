package net.muxigame.core.worldrolesfullqa;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.*;
import net.minecraft.resources.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.util.Unit;
import dev.ryanhcode.sable.api.sublevel.*;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.*;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
@Mod("muxi_world_roles_full_qa")
public final class FullWorldRolesLifecycleQA {
 int ticks;boolean done;final List<String> checks=new ArrayList<>();
 final String stage=System.getProperty("qa.worldRoles.fullStage","");
 final Path expected=Path.of("qa-world/data/world-roles-full-expected.json");
 public FullWorldRolesLifecycleQA(){NeoForge.EVENT_BUS.addListener(this::tick);}
 ResourceKey<Level> key(String id){return ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(id));}
 ServerLevel level(MinecraftServer s,String id){return Objects.requireNonNull(s.getLevel(key(id)),"Missing level "+id);}
 void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
 ServerLevel home(MinecraftServer s){return level(s,stage.equals("verify-new")?"muxi_game_core:homeworld":"minecraft:overworld");}
 ServerLevel survival(MinecraftServer s){return level(s,stage.equals("verify-new")?"minecraft:overworld":"muxi_game_core:overworld");}
 JsonObject row(ServerLevel level,ServerSubLevel body,UUID pig)throws Exception {
  var tag=SubLevelSerializer.toData(body,List.of()).fullTag();var plot=tag.getCompound("plot");var center=body.getPlot().getCenterBlock().atY(70);var data=new JsonObject();data.addProperty("uuid",body.getUniqueId().toString());data.addProperty("name",body.getName());data.addProperty("dimension",level.dimension().location().toString());data.addProperty("pigUUID",pig.toString());data.addProperty("pigHealth",27.5);data.addProperty("plot_x",plot.getInt("plot_x"));data.addProperty("plot_z",plot.getInt("plot_z"));data.addProperty("log_size",plot.getInt("log_size"));data.addProperty("blockX",center.getX());data.addProperty("blockY",center.getY());data.addProperty("blockZ",center.getZ());return data;
 }
 void seed(MinecraftServer s)throws Exception {
  var result=new JsonObject();check(home(s).getSeed()==8324402536652151177L&&survival(s).getSeed()==8324402536652151177L,"frozen synthetic global seed");
  for(String role:List.of("home","survival")){
   ServerLevel level=role.equals("home")?home(s):survival(s);var container=SubLevelContainer.getContainer(level);var pose=new Pose3d();
   ServerSubLevel body=(ServerSubLevel)container.allocateNewSubLevel(pose);body.setName("world-role-full-qa-"+role);var user=new CompoundTag();user.putString("dimension",level.dimension().location().toString());body.setUserDataTag(user);
   BlockPos block=body.getPlot().getCenterBlock().atY(70);level.getChunk(block);check(level.setBlock(block,Blocks.OAK_PLANKS.defaultBlockState(),3),"actual Sable plot block placed "+role);check(container.addForceLoadTicket(body,SubLevelLoadingTicketType.COMMAND_FORCED,Unit.INSTANCE),"actual Sable persistent force-load ticket "+role);
   var pig=EntityType.PIG.create(level);pig.moveTo(0.5,120,0.5,0,0);Objects.requireNonNull(pig.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(30);pig.setHealth(27.5f);pig.setNoGravity(true);check(level.addFreshEntity(pig),"seed carried entity "+role);result.add(role,row(level,body,pig.getUUID()));
  }
  var spawn=home(s).getSharedSpawnPos();result.addProperty("homeSpawnX",spawn.getX());result.addProperty("homeSpawnY",spawn.getY());result.addProperty("homeSpawnZ",spawn.getZ());Files.createDirectories(expected.getParent());Files.writeString(expected,new GsonBuilder().setPrettyPrinting().create().toJson(result));
 }
 void verify(MinecraftServer s)throws Exception {
  var data=JsonParser.parseString(Files.readString(expected)).getAsJsonObject();boolean migrated=stage.equals("verify-new");
  check(s.getLevel(key("muxi_game_core:adventure"))!=null,"adventure registered");check(s.getLevel(Level.NETHER)!=null&&s.getLevel(Level.END)!=null,"original Nether and End registered");
  check(migrated==(s.getLevel(key("muxi_game_core:homeworld"))!=null),"homeworld registration matches layout");check(migrated==(s.getLevel(key("muxi_game_core:overworld"))==null),"legacy survival registration matches layout");
  if(migrated){Class<?> policy=Class.forName("net.muxigame.core.feature.spawning.HomeSpawningFeature");var method=policy.getMethod("protectedHome",net.minecraft.world.level.Level.class);check((Boolean)method.invoke(null,home(s)),"home generation/spawn guard active");check(!(Boolean)method.invoke(null,survival(s)),"primary survival excluded from home ban");}
  for(String role:List.of("home","survival")){
   var saved=data.getAsJsonObject(role);ServerLevel level=role.equals("home")?home(s):survival(s);var container=SubLevelContainer.getContainer(level);UUID id=UUID.fromString(saved.get("uuid").getAsString());var body=(ServerSubLevel)container.getSubLevel(id);check(body!=null,"actual forced construct cold loaded "+role);check(body.getUniqueId().equals(id)&&body.getName().equals(saved.get("name").getAsString()),"construct UUID and name preserved "+role);
   var plot=SubLevelSerializer.toData(body,List.of()).fullTag().getCompound("plot");check(plot.getInt("plot_x")==saved.get("plot_x").getAsInt()&&plot.getInt("plot_z")==saved.get("plot_z").getAsInt()&&plot.getInt("log_size")==saved.get("log_size").getAsInt(),"construct plot addressing preserved "+role);
   var block=new BlockPos(saved.get("blockX").getAsInt(),saved.get("blockY").getAsInt(),saved.get("blockZ").getAsInt());check(level.getBlockState(block).is(Blocks.OAK_PLANKS),"actual plot block cold reloaded "+role);check(body.getUserDataTag().getString("dimension").equals(level.dimension().location().toString()),"construct parent dimension reference migrated "+role);check(container.getAllTickets().containsKey(id),"construct force ticket cold reloaded "+role);
   level.getChunk(new BlockPos(0,120,0));var pig=level.getEntity(UUID.fromString(saved.get("pigUUID").getAsString()));check(pig instanceof LivingEntity&&((LivingEntity)pig).getHealth()==27.5f,"carried entity UUID and health reloaded "+role);
  }
  if(migrated){var spawn=home(s).getSharedSpawnPos();check(spawn.equals(new BlockPos(data.get("homeSpawnX").getAsInt(),data.get("homeSpawnY").getAsInt(),data.get("homeSpawnZ").getAsInt())),"original independent home spawn cold reloaded");var end=s.getLevel(Level.END);var pig=EntityType.PIG.create(end);pig.moveTo(0,120,0,0,0);var dest=((EndPortalBlock)Blocks.END_PORTAL).getPortalDestination(end,pig,new BlockPos(0,120,0));check(dest!=null&&dest.newLevel()==home(s),"actual End return resolves preserved home hub");}
 }
 void tick(ServerTickEvent.Post event){if(done)return;ticks++;try{if(!stage.equals("seed-old")&&ticks==20){home(event.getServer()).getChunk(new BlockPos(0,120,0));survival(event.getServer()).getChunk(new BlockPos(0,120,0));}if(stage.equals("seed-old")&&ticks==40){seed(event.getServer());}if(ticks>=120){if(!stage.equals("seed-old"))verify(event.getServer());finish(true,"");}}catch(Throwable error){try{finish(false,error.toString());}catch(Exception io){io.printStackTrace();}error.printStackTrace();}}
 void finish(boolean success,String error)throws Exception{done=true;var result=new LinkedHashMap<String,Object>();result.put("success",success);result.put("stage",stage);result.put("checks",checks);result.put("error",error);result.put("productionMutation",false);Files.writeString(Path.of("world-roles-full-lifecycle-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));String root=System.getProperty("qa.local.root"),run=System.getProperty("qa.local.runId");if(root!=null&&run!=null)Files.writeString(Path.of(root,"stop-request.json"),new Gson().toJson(Map.of("runId",run,"normalStopOnly",true,"requestedBy","world-roles-full-lifecycle-qa")));}
}
