"""Actual guard code with isolated MC/Waystones phase fixtures; no game, accounts or network."""
from pathlib import Path
import argparse,hashlib,json,subprocess
REPO=Path(__file__).resolve().parents[1]
REL='src/main/java/net/muxigame/core/feature/waystones/network/NetworkTeleportGuard.java'
parser=argparse.ArgumentParser(description='Regress native preparation readiness without starting Minecraft')
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--output',type=Path,default=REPO/'build/network-target-preparation-tests')
parser.add_argument('--baseline-source',type=Path)
args=parser.parse_args()
JDK=args.java_home;OUT=args.output;OUT.mkdir(parents=True,exist_ok=True)
files={
'net/minecraft/resources/ResourceLocation.java':'package net.minecraft.resources;public record ResourceLocation(String value){}',
'net/minecraft/resources/Dimension.java':'package net.minecraft.resources;public record Dimension(ResourceLocation location){}',
'net/minecraft/core/BlockPos.java':'package net.minecraft.core;public record BlockPos(int x,int y,int z){public BlockPos immutable(){return this;}}',
'net/minecraft/network/chat/Component.java':'package net.minecraft.network.chat;public record Component(String text){public static Component literal(String text){return new Component(text);}}',
'net/minecraft/world/entity/Entity.java':'package net.minecraft.world.entity;public class Entity{}',
'net/minecraft/server/level/ServerLevel.java':'package net.minecraft.server.level;public class ServerLevel{public net.minecraft.resources.Dimension dimension;public boolean loaded=true,ready,physical=true;public java.util.UUID physicalUid;public net.minecraft.resources.Dimension dimension(){return dimension;}public boolean hasChunkAt(net.minecraft.core.BlockPos pos){return loaded;}}',
'net/minecraft/server/MinecraftServer.java':'package net.minecraft.server;public class MinecraftServer{public java.util.Map<net.minecraft.resources.Dimension,net.minecraft.server.level.ServerLevel> levels=new java.util.HashMap<>();public net.minecraft.server.level.ServerPlayer current;public net.minecraft.server.level.ServerLevel getLevel(net.minecraft.resources.Dimension dim){return levels.get(dim);}public List getPlayerList(){return new List();}public class List{public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return current!=null&&current.id.equals(id)?current:null;}}}',
'net/minecraft/server/level/ServerPlayer.java':'package net.minecraft.server.level;public class ServerPlayer extends net.minecraft.world.entity.Entity{public Object connection=new Object();public net.minecraft.server.MinecraftServer server;public ServerLevel level;public int tickCount;public boolean alive=true,disconnected;public java.util.UUID id=java.util.UUID.randomUUID();public ServerLevel serverLevel(){return level;}public boolean isAlive(){return alive;}public boolean hasDisconnected(){return disconnected;}public java.util.UUID getUUID(){return id;}}',
'net/blay09/mods/waystones/api/Waystone.java':'package net.blay09.mods.waystones.api;public interface Waystone{boolean isValid();java.util.UUID getWaystoneUid();net.minecraft.resources.Dimension getDimension();net.minecraft.core.BlockPos getPos();net.minecraft.resources.ResourceLocation getWaystoneType();}',
'net/blay09/mods/waystones/api/WaystoneTeleportContext.java':'package net.blay09.mods.waystones.api;public interface WaystoneTeleportContext{net.minecraft.world.entity.Entity getEntity();Waystone getTargetWaystone();}',
'net/blay09/mods/waystones/api/WaystoneTypes.java':'package net.blay09.mods.waystones.api;public class WaystoneTypes{public static boolean isSharestone(net.minecraft.resources.ResourceLocation type){return type.value().equals("share");}}',
'net/blay09/mods/waystones/api/WaystonesAPI.java':'package net.blay09.mods.waystones.api;public class WaystonesAPI{public static java.util.Map<java.util.UUID,Waystone> registry=new java.util.HashMap<>();public static boolean activated=true;public static java.util.Optional<Waystone> getWaystone(net.minecraft.server.MinecraftServer server,java.util.UUID id){return java.util.Optional.ofNullable(registry.get(id));}public static boolean isWaystoneActivated(net.minecraft.server.level.ServerPlayer player,Waystone stone){return activated;}}',
'net/blay09/mods/waystones/api/error/WaystoneTeleportError.java':'package net.blay09.mods.waystones.api.error;public record WaystoneTeleportError(net.minecraft.network.chat.Component message){}',
'net/blay09/mods/waystones/api/EntityTeleportResult.java':'package net.blay09.mods.waystones.api;public record EntityTeleportResult(net.blay09.mods.waystones.api.error.WaystoneTeleportError error){public static EntityTeleportResult failed(Object entity,Object destination,net.blay09.mods.waystones.api.error.WaystoneTeleportError error){return new EntityTeleportResult(error);}}',
'net/blay09/mods/waystones/api/event/WaystoneTeleportEntityEvent.java':'package net.blay09.mods.waystones.api.event;public class WaystoneTeleportEntityEvent{public static class Pre{public net.blay09.mods.waystones.api.WaystoneTeleportContext context;public net.minecraft.world.entity.Entity entity;public boolean denied;public net.minecraft.world.entity.Entity getEntity(){return entity;}public net.blay09.mods.waystones.api.WaystoneTeleportContext getContext(){return context;}public Object getOriginalDestination(){return this;}public void overrideResult(net.blay09.mods.waystones.api.EntityTeleportResult result){denied=true;}}}',
'net/blay09/mods/balm/api/Balm.java':'package net.blay09.mods.balm.api;public class Balm{public static Events events=new Events();public static Events getEvents(){return events;}public static class Events{public java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent.Pre> listener;public void onEvent(Class<net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent.Pre> type,java.util.function.Consumer<net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent.Pre> fn){listener=fn;}}}',
'net/muxigame/minigames/GameRuntime.java':'package net.muxigame.minigames;public class GameRuntime{public static boolean blocked;public static boolean blocksWorldTravel(net.minecraft.server.level.ServerPlayer player){return blocked;}}',
'net/muxigame/core/feature/waystones/network/NetworkFacilityConfig.java':'package net.muxigame.core.feature.waystones.network;public class NetworkFacilityConfig{public record Facility(String name){}public java.util.List<Facility> facilities=new java.util.ArrayList<>();public java.util.List<Facility> facilities(){return facilities;}}',
'net/muxigame/core/feature/waystones/network/NetworkPortalFacilities.java':'package net.muxigame.core.feature.waystones.network;public class NetworkPortalFacilities{public record Source(net.blay09.mods.waystones.api.Waystone stone,NetworkFacilityConfig.Facility facility,String frameKey){}public static NetworkFacilityConfig cfg;public static Source current;public static boolean gateway=true;public static int physicalReads;public static NetworkFacilityConfig config(){return cfg;}public static Source source(net.minecraft.server.level.ServerPlayer p,NetworkFacilityConfig c){return current;}public static boolean currentDimensionGateway(net.minecraft.server.level.ServerPlayer p,NetworkFacilityConfig c){return gateway;}public static boolean actualStone(net.minecraft.server.level.ServerLevel level,net.blay09.mods.waystones.api.Waystone stone){physicalReads++;return level.loaded&&level.ready&&level.physical&&stone.getWaystoneUid().equals(level.physicalUid);}}',
'net/muxigame/core/feature/waystones/network/TargetPreparationTest.java':r'''package net.muxigame.core.feature.waystones.network;
import net.blay09.mods.waystones.api.*;import net.minecraft.server.*;import net.minecraft.server.level.*;import net.minecraft.resources.*;import net.minecraft.core.*;import java.util.*;
public class TargetPreparationTest{
static int checks;static final Dimension HOME=new Dimension(new ResourceLocation("minecraft:overworld")),CROSS=new Dimension(new ResourceLocation("muxi_game_core:overworld"));
static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
static class Stone implements Waystone{UUID id=UUID.randomUUID();Dimension dim;BlockPos pos;boolean valid=true;ResourceLocation type=new ResourceLocation("waystone");Stone(Dimension d,int y){dim=d;pos=new BlockPos(0,y,0);}public boolean isValid(){return valid;}public UUID getWaystoneUid(){return id;}public Dimension getDimension(){return dim;}public BlockPos getPos(){return pos;}public ResourceLocation getWaystoneType(){return type;}}
static class Context implements WaystoneTeleportContext{ServerPlayer player;Stone target;public net.minecraft.world.entity.Entity getEntity(){return player;}public Waystone getTargetWaystone(){return target;}}
static class Fixture{ServerPlayer player=new ServerPlayer();ServerLevel home=new ServerLevel(),cross=new ServerLevel();Stone source=new Stone(HOME,64),target=new Stone(CROSS,65);Context context=new Context();NetworkFacilityConfig.Facility facility=new NetworkFacilityConfig.Facility("registered source");Fixture(){
var server=new MinecraftServer();player.server=server;player.level=home;server.current=player;home.dimension=HOME;cross.dimension=CROSS;server.levels.put(HOME,home);server.levels.put(CROSS,cross);cross.physicalUid=target.id;
NetworkPortalFacilities.cfg=new NetworkFacilityConfig();NetworkPortalFacilities.cfg.facilities.add(facility);NetworkPortalFacilities.current=new NetworkPortalFacilities.Source(source,facility,"frame");NetworkPortalFacilities.gateway=true;NetworkPortalFacilities.physicalReads=0;
WaystonesAPI.registry.clear();WaystonesAPI.registry.put(target.id,target);WaystonesAPI.activated=true;net.muxigame.minigames.GameRuntime.blocked=false;context.player=player;context.target=target;NetworkTeleportGuard.track(context,player,NetworkPortalFacilities.current,target);
}String reason(boolean prepared){return NetworkTeleportGuard.denial(context,prepared);}void denyBefore(String reason){check(reason(false)!=null,reason);}}
public static void main(String[]args){
var f=new Fixture();check(f.reason(false)==null,"loaded chunk pending Waystones onLoad should wait for native preparation");check(NetworkPortalFacilities.physicalReads==0,"chunk presence alone never reads an unready block entity");
check(f.reason(true)!=null,"prepared hook rejects a still-invalid physical target");check(f.reason(false)!=null,"cost hook remembers preparation and cannot skip the failed physical target");
f=new Fixture();f.cross.loaded=false;check(f.reason(false)==null,"unloaded target remains owned by native chunk preparation");check(NetworkPortalFacilities.physicalReads==0,"unloaded target is not force-loaded by guard");check(f.reason(true)!=null,"prepared target cannot remain unloaded");
f=new Fixture();f.cross.ready=true;check(f.reason(true)==null,"native prepared target with exact UID is accepted");check(f.reason(false)==null,"valid cost recheck accepted");f.cross.physical=false;check(f.reason(false)!=null,"stone removed after pending validation is rejected before cost");
f=new Fixture();f.cross.ready=true;check(f.reason(true)==null,"second live preparation accepted");f.cross.physicalUid=UUID.randomUUID();check(f.reason(false)!=null,"replacement stone cannot inherit target UID");
f=new Fixture();f.target.pos=new BlockPos(0,64,0);f.denyBefore("registered target movement rejected before preparation");
f=new Fixture();f.target.dim=HOME;f.denyBefore("registered target dimension change rejected before preparation");
f=new Fixture();WaystonesAPI.registry.remove(f.target.id);f.denyBefore("missing target registry rejected before preparation");
f=new Fixture();f.target.valid=false;f.denyBefore("invalid registry target rejected before preparation");
f=new Fixture();WaystonesAPI.activated=false;f.denyBefore("unactivated stone rejected before preparation");
f=new Fixture();NetworkPortalFacilities.gateway=false;f.denyBefore("unregistered source gate cannot cross dimensions");
f=new Fixture();NetworkPortalFacilities.current=null;f.denyBefore("missing entrance rejected before preparation");
f=new Fixture();NetworkPortalFacilities.current=new NetworkPortalFacilities.Source(new Stone(HOME,64),f.facility,"frame");f.denyBefore("replacement source stone rejected before preparation");
f=new Fixture();f.source.pos=new BlockPos(0,65,0);f.denyBefore("source movement rejected before preparation");
f=new Fixture();NetworkPortalFacilities.current=new NetworkPortalFacilities.Source(f.source,f.facility,"changed frame");f.denyBefore("changed entrance frame rejected before preparation");
f=new Fixture();NetworkPortalFacilities.cfg.facilities.clear();f.denyBefore("source facility deregistration rejected before preparation");
f=new Fixture();f.player.connection=new Object();f.denyBefore("connection replacement rejected before preparation");
f=new Fixture();f.player.disconnected=true;f.denyBefore("disconnected actor rejected before preparation");
f=new Fixture();f.player.server.current=new ServerPlayer();f.denyBefore("player object replacement rejected before preparation");
f=new Fixture();f.player.tickCount=401;f.denyBefore("expired request rejected before preparation");
f=new Fixture();f.player.level=f.cross;f.denyBefore("actor dimension change rejected before preparation");
f=new Fixture();f.player.alive=false;f.denyBefore("dead actor rejected before preparation");
f=new Fixture();net.muxigame.minigames.GameRuntime.blocked=true;f.denyBefore("minigame travel prohibition remains enforced");
f=new Fixture();f.target.type=new ResourceLocation("share");WaystonesAPI.activated=false;check(f.reason(false)==null,"native sharestone activation exception unchanged");
f=new Fixture();NetworkTeleportGuard.untrack(f.context);check(f.reason(true)==null&&f.reason(false)==null,"ordinary untracked Waystones travel remains outside guard");
f=new Fixture();f.cross.ready=true;check(f.reason(true)==null,"entity event starts with prepared live target");f.cross.physical=false;NetworkTeleportGuard.register();var event=new net.blay09.mods.waystones.api.event.WaystoneTeleportEntityEvent.Pre();event.context=f.context;event.entity=f.player;net.blay09.mods.balm.api.Balm.events.listener.accept(event);check(event.denied,"final native entity pre-event still rejects removed target");
System.out.println("{\"success\":true,\"checks\":"+checks+",\"scope\":\"actual NetworkTeleportGuard with phase/world/Waystones fixtures; no Minecraft or actual teleport\"}");
}}
'''
}
paths=[]
for name,data in files.items():
 p=OUT/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,encoding='utf-8');paths.append(p)
old=args.baseline_source.read_bytes() if args.baseline_source else subprocess.check_output(['git','-c','safe.directory='+str(REPO),'show','2bdcbacdc8d878a4829c589c5974e194e1255f2f:'+REL],cwd=REPO)
old_file=OUT/'baseline/NetworkTeleportGuard.java';old_file.parent.mkdir(exist_ok=True);old_file.write_bytes(old)
def run(source,name):
 classes=OUT/name;classes.mkdir(exist_ok=True)
 args=[str(JDK/'bin/javac.exe'),'--release','21','-encoding','UTF-8','-d',str(classes),str(source),*map(str,paths)]
 c=subprocess.run(args,capture_output=True,text=True,encoding='utf-8');
 if c.returncode:raise RuntimeError(c.stderr)
 return subprocess.run([str(JDK/'bin/java.exe'),'-cp',str(classes),'net.muxigame.core.feature.waystones.network.TargetPreparationTest'],capture_output=True,text=True,encoding='utf-8')
baseline=run(old_file,'baseline-classes');assert baseline.returncode!=0 and 'loaded chunk pending Waystones onLoad' in baseline.stderr, 'Baseline must reproduce observed failure'
patched=run(REPO/REL,'patched-classes')
if patched.returncode:raise RuntimeError(patched.stderr)
result=json.loads(patched.stdout);result.update({'baselineReproduced':True,'baselineFailure':'loaded chunk pending Waystones onLoad should wait for native preparation',
 'baselineSourceSha256':hashlib.sha256(old).hexdigest(),'patchedSourceSha256':hashlib.sha256((REPO/REL).read_bytes()).hexdigest(),
 'newMcStarted':False,'nativeCrossDimensionRetestComplete':False})
(OUT/'result.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
