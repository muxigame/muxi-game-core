package net.muxigame.schematicqa;

import com.google.gson.*;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

/** Test-only driver. No global keyboard/mouse/focus calls, external server, or auto-building feature. */
@Mod(value="muxi_schematic_qa",dist=Dist.CLIENT)
public final class SchematicQA {
 public static final AtomicLong layerCalls=new AtomicLong(),drawnChunks=new AtomicLong(),compiledBlocks=new AtomicLong();
 private static final Path BASE=Path.of("C:/Users/ranzh/Documents/Codex/schematic-task23-20261002");
 private static final Path ROOT=Path.of(System.getProperty("muxi.schematicQA.dir",BASE.resolve("client").toString())).toAbsolutePath().normalize();
 private static final String WORLD="schematic-task23-local-only",NAME="Task23 isolated schematic world";
 private final JsonObject result=new JsonObject();
 private final JsonArray shots=new JsonArray(),materials=new JsonArray();
 private long start=System.nanoTime(),renderFrames,stageFrameBase;
 private int stage,age,ticks;
 private boolean started,busy,done,exitQueued,fixtureScheduled;
 private String observedDimension="",fixtureDimension="";
 private int stableWorldTicks;
 private volatile boolean fixtureReady;
 private volatile String fixtureError="";
 private String capturePending;
 private LitematicaSchematic schematic;
 private SchematicPlacement placement;
 private MaterialListBase list;
 private BlockPos origin=new BlockPos(0,80,0);
 public SchematicQA(){
  if(!Boolean.getBoolean("muxi.schematicQA"))return;
  if(!ROOT.startsWith(BASE)||ROOT.equals(BASE)||!Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize().equals(ROOT))throw new IllegalStateException("Task23 private game directory required");
  result.addProperty("runtimeStarted",true);result.addProperty("testOnly",true);result.addProperty("globalKeyboardMouseAutomationUsed",false);
  NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::frame);
 }
 private void write(String name,JsonObject object)throws Exception{Files.writeString(ROOT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(object));}
 private void frame(RenderFrameEvent.Post event){
  ++renderFrames;
  if(capturePending==null)return;
  String name=capturePending;capturePending=null;
  try(var image=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
   image.writeToFile(ROOT.resolve(name));
   JsonObject shot=new JsonObject();shot.addProperty("file",name);shot.addProperty("stage",stage);shot.addProperty("renderFrame",renderFrames);
   shot.addProperty("drawnChunks",drawnChunks.get());shot.addProperty("compiledBlocks",compiledBlocks.get());
   shot.addProperty("shader",shader());shots.add(shot);
  }catch(Exception error){fail(error);}
 }
 private String shader(){try{return String.valueOf(Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName").invoke(null));}catch(Exception e){return "unavailable:"+e.getClass().getSimpleName();}}
 private void shaders(boolean enabled)throws Exception{
  Class<?> iris=Class.forName("net.irisshaders.iris.Iris");Object config=iris.getMethod("getIrisConfig").invoke(null);
  config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);
  config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
 }
 private void next(String label)throws Exception{
  ++stage;age=0;stageFrameBase=renderFrames;
  JsonObject p=new JsonObject();p.addProperty("stage",stage);p.addProperty("label",label);p.addProperty("frames",renderFrames);p.addProperty("drawnChunks",drawnChunks.get());write("runtime-progress.json",p);
 }
 private void capture(String name){capturePending=name;}
 private int ghosts(){
  var world=SchematicWorldHandler.getSchematicWorld();if(world==null)return 0;int count=0;
  for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(world.getBlockState(origin.offset(x,0,z)).is(Blocks.STONE))++count;
  return count;
 }
 private int actualStone(){int count=0;var world=Minecraft.getInstance().level;
  for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(world.getBlockState(origin.offset(x,0,z)).is(Blocks.STONE))++count;return count;
 }
 private void load()throws Exception{
  schematic=LitematicaSchematic.createFromFile(ROOT.resolve("schematics").toFile(),"task23-test-floor.litematic");
  if(schematic==null||schematic.getMetadata().getTotalBlocks()!=9)throw new IllegalStateException("Nine-block fixture did not load");
  var container=schematic.getSubRegionContainer("TestFloor");int blocks=0;
  if(container!=null)for(int x=0;x<3;x++)for(int z=0;z<3;z++)if(container.get(x,0,z).is(Blocks.STONE))++blocks;
  result.addProperty("parsedSchematicStoneBlocks",blocks);if(blocks!=9)throw new IllegalStateException("Parsed fixture must contain nine stone blocks, got "+blocks);
  SchematicHolder.getInstance().addSchematic(schematic,false);
  placement=SchematicPlacement.createFor(schematic,origin,"Task23 9-block projection",true,true);
  DataManager.getSchematicPlacementManager().addSchematicPlacement(placement,false);
  list=placement.getMaterialList();list.reCreateMaterialList();DataManager.setMaterialList(list);
 }
 private int countMaterials(String label)throws Exception{
  JsonObject row=new JsonObject();int total=0,missing=0;
  for(var entry:list.getMaterialsAll()){total+=entry.getCountTotal();missing+=entry.getCountMissing();}
  row.addProperty("stage",label);row.addProperty("total",total);row.addProperty("missing",missing);row.addProperty("realStone",actualStone());materials.add(row);
  return total;
 }
 private void fixture(){
  var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null)return;
  server.execute(()->{try{
   if(!NAME.equals(server.getWorldData().getLevelName()))throw new IllegalStateException("Refuse non-task23 world");
   var player=server.getPlayerList().getPlayer(mc.player.getUUID());if(player==null)throw new IllegalStateException("Test player unavailable");
   var level=player.serverLevel();
   if(!level.dimension().location().toString().equals(fixtureDimension))throw new IllegalStateException("Dimension changed while preparing fixture");
   for(int x=-2;x<=4;x++)for(int z=-2;z<=4;z++){level.setBlock(origin.offset(x,-1,z),Blocks.BLUE_WOOL.defaultBlockState(),3);for(int y=0;y<6;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
   level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
   player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
   player.getInventory().clearContent();player.inventoryMenu.broadcastChanges();
   player.connection.teleport(origin.getX()+1.5,origin.getY()+3.0,origin.getZ()+7.5,180.0f,35.0f);fixtureReady=true;
  }catch(Exception e){fixtureError=e.toString();}});
 }
 private void fail(Throwable error){
  if(done)return;done=true;
  try{result.addProperty("completed",false);result.addProperty("error",error.toString());result.addProperty("stage",stage);result.add("screenshots",shots);write("runtime-result.json",result);}catch(Exception ignored){}
  error.printStackTrace();
  // Keep a failed client visible for diagnosis; no force termination or another process interaction.
 }
 private static void disconnectOwnWorld(Minecraft mc){var server=mc.getSingleplayerServer();if(server!=null){if(!NAME.equals(server.getWorldData().getLevelName()))throw new IllegalStateException("Refuse other world stop");server.halt(false);}if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}
 private void tick(ClientTickEvent.Post event){
  var mc=Minecraft.getInstance();
  if(Files.exists(ROOT.resolve("request-normal-exit.flag"))&&!busy&&!exitQueued){exitQueued=true;done=true;mc.execute(()->{disconnectOwnWorld(mc);mc.stop();});return;}
  if(done||busy)return;busy=true;
  try{
   ++ticks;
   if(System.nanoTime()-start>1_200_000_000_000L)throw new IllegalStateException("Task23 QA deadline exceeded at stage "+stage);
   if(!fixtureError.isEmpty())throw new IllegalStateException(fixtureError);
   if(ticks%100==0){JsonObject p=new JsonObject();p.addProperty("stage",stage);p.addProperty("age",age);p.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());p.addProperty("frames",renderFrames);p.addProperty("ghosts",mc.level==null?0:ghosts());p.addProperty("dimension",mc.level==null?"":mc.level.dimension().location().toString());p.addProperty("origin",origin.toShortString());p.addProperty("clientOriginChunkLoaded",mc.level!=null&&mc.level.hasChunkAt(origin));p.addProperty("fixtureReady",fixtureReady);write("runtime-progress.json",p);}
   if(!started){
    if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;started=true;
    mc.getWindow().setTitle("Task23 schematic isolated QA");mc.options.renderDistance().set(3);mc.options.bobView().set(false);
    mc.options.setCameraType(CameraType.FIRST_PERSON);
    if(mc.getLevelSource().levelExists(WORLD))throw new IllegalStateException("Fresh task23 world expected");
    mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(NAME,GameType.CREATIVE,false,Difficulty.PEACEFUL,false,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(23002101L,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
   }
   if(stage==15){if(mc.level!=null||mc.getSingleplayerServer()!=null)return;if(++age<100)return;capture("15-title-after-world-close.png");next("normal-client-stop");return;}
   if(stage==16){if(capturePending!=null||++age<20)return;result.addProperty("completed",true);result.addProperty("normalStopRequested",true);result.addProperty("manualKeyboardPlacementVerified",false);result.addProperty("drawnChunks",drawnChunks.get());result.addProperty("compiledBlocks",compiledBlocks.get());result.add("screenshots",shots);result.add("materials",materials);write("runtime-result.json",result);done=true;mc.stop();return;}
   if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
   if(capturePending!=null)return;
   ++age;
   if(stage==0){
    if(age>1800)throw new IllegalStateException("Fixture readiness timeout: dimension="+mc.level.dimension().location()+", origin="+origin+", ready="+fixtureReady+", loaded="+mc.level.hasChunkAt(origin));
    mc.setScreen(null);String currentDimension=mc.level.dimension().location().toString();
    if(!currentDimension.equals(observedDimension)){observedDimension=currentDimension;stableWorldTicks=0;fixtureScheduled=false;fixtureReady=false;}
    if(!fixtureScheduled){if(++stableWorldTicks<300||!mc.level.hasChunkAt(mc.player.blockPosition()))return;
     origin=new BlockPos(mc.player.getBlockX(),Math.min(mc.level.getMaxBuildHeight()-12,Math.max(mc.level.getMinBuildHeight()+12,mc.player.getBlockY()+4)),mc.player.getBlockZ());
     fixtureDimension=currentDimension;fixtureScheduled=true;fixture();return;}
    if(!fixtureReady||!mc.level.hasChunkAt(origin)||!mc.level.getBlockState(origin.below()).is(Blocks.BLUE_WOOL)||!mc.level.getBlockState(origin.offset(2,-1,2)).is(Blocks.BLUE_WOOL))return;
    mc.player.getInventory().clearContent();
    JsonObject versions=new JsonObject();for(var mod:ModList.get().getMods())if(Set.of("minecraft","neoforge","forgematica","litematica","mafglib","malilib","sodium","iris","sable","yes_steve_model","muxi_game_core","muxi_terminal").contains(mod.getModId()))versions.addProperty(mod.getModId(),mod.getVersion().toString());
    result.add("versions",versions);result.addProperty("gpuRenderer",GL11.glGetString(GL11.GL_RENDERER));result.addProperty("playerRenderer",mc.getEntityRenderDispatcher().getRenderer(mc.player).getClass().getName());result.addProperty("worldDimension",mc.level.dimension().location().toString());
    result.addProperty("fixtureOrigin",origin.toShortString());result.addProperty("fixtureClientPlatformVerified",true);
    result.addProperty("easyPlaceEnabled",Configs.Generic.EASY_PLACE_MODE.getBooleanValue());if(Configs.Generic.EASY_PLACE_MODE.getBooleanValue())throw new IllegalStateException("Easy Place unexpectedly enabled");
    capture("00-baseline-shader-on.png");next("load-nine-block-schematic");return;
   }
   if(stage==1){if(capturePending!=null)return;if(age==1)load();else if(schematic==null)load();if(age<140)return;
    if(ghosts()!=9&&age<600)return;
    if(ghosts()!=9||actualStone()!=0||countMaterials("loaded")!=9||drawnChunks.get()==0)throw new IllegalStateException("Projection render/material check failed: ghosts="+ghosts()+", actual="+actualStone()+", drawnChunks="+drawnChunks.get());
    result.addProperty("initialGhosts",ghosts());capture("01-nine-ghosts-shader-on.png");next("third-person-ysm-combination");return;
   }
   if(stage==2){if(age==1)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);if(age<100)return;capture("02-third-person-ysm-shader-on.png");next("main-menu-ui");return;}
   if(stage==3){if(age==1)mc.setScreen(new GuiMainMenu());if(age<80)return;capture("03-forgematica-main-menu.png");next("material-list-ui");return;}
   if(stage==4){if(age==1)mc.setScreen(new GuiMaterialList(list));if(age<80)return;capture("04-material-list-nine-stone.png");next("disable-shader-in-private-instance");return;}
   if(stage==5){if(capturePending!=null)return;if(age<=2){mc.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);shaders(false);}if(age<180)return;capture("05-nine-ghosts-shader-off.png");next("move-and-rotate-placement");return;}
   if(stage==6){if(age==1){placement.setOrigin(origin.offset(4,0,0),null);placement.setRotation(Rotation.CLOCKWISE_90,null);}if(age<100)return;capture("06-placement-moved-rotated.png");result.addProperty("movedOrigin",placement.getOrigin().toShortString());result.addProperty("rotation",placement.getRotation().name());next("restore-placement");return;}
   if(stage==7){if(age==1){placement.setRotation(Rotation.NONE,null);placement.setOrigin(origin,null);}if(age<100)return;if(ghosts()!=9)throw new IllegalStateException("Restored placement missing");capture("07-placement-restored.png");next("unload-schematic");return;}
   if(stage==8){if(age==1){DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);SchematicHolder.getInstance().removeSchematic(schematic);}if(age<100)return;capture("08-unloaded-no-ghosts.png");next("reload-schematic");return;}
   if(stage==9){if(age==1)load();if(age<140)return;if(ghosts()!=9)throw new IllegalStateException("Reload missing");capture("09-reloaded-nine-ghosts.png");next("single-test-fixture-block-update");return;}
   if(stage==10){if(age==1){var server=mc.getSingleplayerServer();server.execute(()->{var player=server.getPlayerList().getPlayer(mc.player.getUUID());player.getInventory().selected=0;player.getInventory().setItem(0,new ItemStack(Blocks.STONE));player.inventoryMenu.broadcastChanges();player.connection.teleport(origin.getX()+1.5,origin.getY()+2.0,origin.getZ()+4.5,180.0f,35.0f);});}
    if(age==30){mc.player.getInventory().selected=0;if(!mc.player.getMainHandItem().is(Blocks.STONE.asItem()))throw new IllegalStateException("Normal stone item not synchronized");var interaction=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5),Direction.UP,origin.below(),false));result.addProperty("standardPlayerPlacementResult",interaction.toString());}
    if(age==80)list.reCreateMaterialList();if(age<160)return;
    if(actualStone()!=1||countMaterials("one-real-fixture-block")!=9)throw new IllegalStateException("Material refresh failed");int missing=0;for(var entry:list.getMaterialsAll())missing+=entry.getCountMissing();if(missing!=8)throw new IllegalStateException("Expected eight missing after fixture update, got "+missing);
    result.addProperty("standardPlayerPlacementVerified",true);result.addProperty("fixtureCountUpdateVerified",true);capture("10-eight-missing-one-real-block.png");next("updated-material-ui");return;
   }
   if(stage==11){if(age==1)mc.setScreen(new GuiMaterialList(list));if(age<80)return;capture("11-material-list-eight-missing.png");next("restore-shader-on");return;}
   if(stage==12){if(capturePending!=null)return;if(age<=2){mc.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);shaders(true);}if(age<180)return;capture("12-ysm-shader-restored.png");next("unload-before-normal-exit");return;}
   if(stage==13){if(age==1){DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);SchematicHolder.getInstance().removeSchematic(schematic);}if(age<100)return;capture("13-clean-unload-before-exit.png");next("normal-world-disconnect");return;}
   if(stage==14){if(capturePending!=null)return;next("await-world-save-and-title");disconnectOwnWorld(mc);return;}
  }catch(Throwable error){fail(error);}finally{busy=false;}
 }
}
