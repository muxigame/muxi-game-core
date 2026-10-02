package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.*;
import net.muxigame.core.feature.dimensions.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.nio.file.*;
import java.util.*;

@Mod("muxi_tasks_smoke")
public final class PortalSmoke {
    private final List<String> passed=new ArrayList<>();
    private boolean done,veto;
    private int ticks;
    public PortalSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::protect);}
    private void protect(BlockEvent.EntityMultiPlaceEvent e){if(veto)e.setCanceled(true);}
    private void check(String name,boolean ok){if(!ok)throw new AssertionError(name);passed.add(name);}
    @SuppressWarnings("unchecked")
    private ServerPlayer player(MinecraftServer server) throws Exception {
        var profile=new GameProfile(UUID.randomUUID(),"PortalQA");var p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        p.connection=new ServerGamePacketListenerImpl(server,connection,p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(Packet<?> packet){}};
        var list=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");list.setAccessible(true);((List<ServerPlayer>)list.get(server.getPlayerList())).add(p);
        var ids=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(p.getUUID(),p);
        server.overworld().addNewPlayer(p);p.setPos(0.5,180,2.5);p.getInventory().setItem(0,new ItemStack(Items.FLINT_AND_STEEL));p.getInventory().setItem(4,new ItemStack(Items.DIAMOND,23));p.giveExperienceLevels(7);return p;
    }
    private void frame(ServerLevel level,WorldPortals.Frame frame,Block material) {
        for(int x=0;x<4;x++)for(int y=0;y<5;y++)level.setBlock(frame.at(x,y),(x==0||x==3||y==0||y==4?material:Blocks.AIR).defaultBlockState(),18);
        for(int x=-1;x<=4;x++)for(int z=-1;z<=1;z++)level.setBlock(frame.normal(x,0,z),material.defaultBlockState(),18);
    }
    private void ignite(ServerPlayer p,WorldPortals.Frame frame) {
        var hit=new BlockHitResult(Vec3.atCenterOf(frame.origin()),Direction.UP,frame.origin(),false);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,frame.origin(),hit));
    }
    private void trip(ServerPlayer player,int destination,Block material) {
        ServerLevel home=player.serverLevel();var gate=new WorldPortals.Frame(new BlockPos(destination*24,179,0),Direction.Axis.X,destination);
        frame(home,gate,material);ignite(player,gate);
        check("physical gate ignites "+destination,WorldPortals.valid(home,gate,true));
        Vec3 originalPosition=new Vec3(gate.origin().getX()+1.75,gate.origin().getY()+1.25,gate.origin().getZ()+0.5);
        player.setPos(originalPosition);
        if(material==Blocks.GRASS_BLOCK) {
            home.setBlockAndUpdate(gate.at(0,2),Blocks.DIRT.defaultBlockState());
            check("grass becoming dirt preserves active gate",WorldPortals.valid(home,gate,true));
        }
        var transition=WorldPortals.block(destination).getPortalDestination(home,player,gate.at(1,1));
        check("gate resolves target "+destination,transition!=null&&transition.newLevel().dimension().equals(WorldDimensions.ALL.get(destination).key()));
        var target=transition.newLevel();player.changeDimension(transition);
        check("gate changes actual dimension "+destination,player.level()==target);
        check("inventory and xp remain shared "+destination,player.getInventory().getItem(4).getCount()==23&&player.experienceLevel==7);
        var returnBlock=target.getBlockState(player.blockPosition()).getBlock();
        check("automatic return gate exists "+destination,returnBlock==WorldPortals.HOME.get());
        var reverse=WorldPortals.HOME.get().getPortalDestination(target,player,player.blockPosition());
        check("paired gate points to original home "+destination,reverse!=null&&reverse.newLevel()==home&&reverse.pos().distanceTo(originalPosition)<0.01);
        player.changeDimension(reverse);
        check("physical roundtrip completed "+destination,player.level()==home);
        var link=PortalLinks.get(player.server).endpoint(gate.key(home));
        check("pairing is serializable "+destination,link.contains("pos"));
        home.setBlockAndUpdate(gate.at(0,2),Blocks.AIR.defaultBlockState());
        check("broken frame cannot transport "+destination,WorldPortals.block(destination).getPortalDestination(home,player,gate.at(1,1))==null);
    }
    private void exercise(MinecraftServer server) throws Exception {
        var p=player(server);
        exactCoordinates(server,p);
        try {server.getCommands().getDispatcher().execute("muxiworld overworld",p.createCommandSourceStack().withPermission(0));throw new AssertionError("Players must use gates");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){passed.add("ordinary players cannot bypass gates with debug command");}
        for(Block material:List.of(Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.STONE,Blocks.COBBLESTONE,Blocks.QUARTZ_BLOCK)) {
            trip(p,1,material);passed.add("roundtrip frame material "+BuiltInRegistries.BLOCK.getKey(material));
        }
        var protectedGate=new WorldPortals.Frame(new BlockPos(80,179,0),Direction.Axis.X,1);
        frame(server.overworld(),protectedGate,Blocks.GRASS_BLOCK);veto=true;ignite(p,protectedGate);veto=false;
        check("protection veto rolls back portal fill",server.overworld().isEmptyBlock(protectedGate.at(1,1)));
        var fireMethod=BaseFireBlock.class.getDeclaredMethod("inPortalDimension",Level.class);fireMethod.setAccessible(true);
        check("home still allows ordinary Nether ignition",(boolean)fireMethod.invoke(null,server.overworld()));
        check("Nether still allows return portal ignition",(boolean)fireMethod.invoke(null,server.getLevel(Level.NETHER)));
        var tf=server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.parse("twilightforest:twilight_forest")));
        check("real Twilight dimension loaded",tf!=null);
        var tfCheck=Class.forName("twilightforest.events.ProgressionEvents").getDeclaredMethod("checkForPortalCreation",ServerPlayer.class,Level.class,float.class);tfCheck.setAccessible(true);
        var config=Class.forName("twilightforest.config.TFConfig");config.getField("allowPortalsInOtherDimensions").setBoolean(null,true);
        for(var key:WorldDimensions.EXPLORATION) {
            ServerLevel level=server.getLevel(key);p.teleportTo(level,0.5,181,0.5,0,0);
            check("adventure Nether ignition blocked in "+key.location(),!(boolean)fireMethod.invoke(null,level));
            for(var remote:List.of(Level.NETHER,Level.END,tf.dimension())) {
                var travel=new EntityTravelToDimensionEvent(p,remote);NeoForge.EVENT_BUS.post(travel);
                check("adventure travel blocked to "+remote.location()+" from "+key.location(),travel.isCanceled());
            }
            var homeTravel=new EntityTravelToDimensionEvent(p,Level.OVERWORLD);NeoForge.EVENT_BUS.post(homeTravel);
            check("home gate travel allowed from "+key.location(),!homeTravel.isCanceled());
            BlockPos eye=new BlockPos(0,179,5);level.setBlockAndUpdate(eye,Blocks.END_PORTAL_FRAME.defaultBlockState());
            p.getInventory().setItem(0,new ItemStack(Items.ENDER_EYE));
            var click=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,eye,new BlockHitResult(Vec3.atCenterOf(eye),Direction.UP,eye,false));NeoForge.EVENT_BUS.post(click);
            check("End eye activation blocked "+key.location(),click.isCanceled()&&!level.getBlockState(eye).getValue(EndPortalFrameBlock.HAS_EYE));
            // A canonical 2x2 pool, dirt edge and flowers; test the real TF detector even with its global allow flag enabled.
            BlockPos water=new BlockPos(0,179,0);
            for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++) {
                level.setBlockAndUpdate(water.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
                boolean inner=x>=0&&x<=1&&z>=0&&z<=1;
                level.setBlockAndUpdate(water.offset(x,0,z),(inner?Blocks.WATER:Blocks.GRASS_BLOCK).defaultBlockState());
                level.setBlockAndUpdate(water.offset(x,1,z),(inner?Blocks.AIR:Blocks.DANDELION).defaultBlockState());
            }
            var diamond=new ItemEntity(level,0.5,179.5,0.5,new ItemStack(Items.DIAMOND));diamond.setThrower(p);level.addFreshEntity(diamond);p.setPos(0.5,181,0.5);
            tfCheck.invoke(null,p,level,8f);
            check("Twilight formation blocked despite permissive global config "+key.location(),level.getBlockState(water).is(Blocks.WATER)&&diamond.getItem().getCount()==1);
            var newReturn=new WorldPortals.Frame(new BlockPos(96,179,0),Direction.Axis.Z,0);
            frame(level,newReturn,Blocks.QUARTZ_BLOCK);
            p.getInventory().setItem(0,new ItemStack(Items.FLINT_AND_STEEL));ignite(p,newReturn);
            check("new quartz return frame ignites on Z axis",WorldPortals.valid(level,newReturn,true));
            p.setPos(96.5,180,1.5);
            var newReturnTrip=WorldPortals.HOME.get().getPortalDestination(level,p,newReturn.at(1,1));
            check("new survival-built gate resolves home",newReturnTrip!=null&&newReturnTrip.newLevel()==server.overworld());
            p.changeDimension(newReturnTrip);
            check("new survival-built gate actually returns home",p.level()==server.overworld());
        }
        p.teleportTo(server.overworld(),0.5,180,0.5,0,0);
        for(var remote:List.of(Level.NETHER,Level.END,tf.dimension())) {
            var travel=new EntityTravelToDimensionEvent(p,remote);NeoForge.EVENT_BUS.post(travel);
            check("home adventure travel retained "+remote.location(),!travel.isCanceled());
        }
        homeAdventurePortals(server,p,tfCheck);
        server.saveEverything(false,true,true);
        check("portal pairs saved on disk",Files.isRegularFile(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/muxi_world_portals.dat")));
    }
    private void active(ServerLevel level,WorldPortals.Frame gate,Block material) {
        frame(level,gate,material);
        for(int w=1;w<=2;w++)for(int h=1;h<=3;h++)level.setBlock(gate.at(w,h),
            WorldPortals.block(gate.destination()).defaultBlockState().setValue(NetherPortalBlock.AXIS,gate.axis()),18);
    }
    private Map<BlockPos,net.minecraft.world.level.block.state.BlockState> footprint(ServerLevel level,WorldPortals.Frame gate) {
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> result=new LinkedHashMap<>();
        for(int w=0;w<4;w++)for(int h=0;h<5;h++)result.put(gate.at(w,h),level.getBlockState(gate.at(w,h)));
        for(int w=1;w<=2;w++)for(int d:List.of(-1,1))for(int h=0;h<=3;h++)result.put(gate.normal(w,h,d),level.getBlockState(gate.normal(w,h,d)));
        return result;
    }
    private void prepare(ServerPlayer p,ServerLevel home,WorldPortals.Frame gate) {
        p.teleportTo(home,gate.origin().getX()+1.5,gate.origin().getY()+1,gate.origin().getZ()+0.5,0,0);
        p.getInventory().setItem(0,new ItemStack(Items.FLINT_AND_STEEL));active(home,gate,Blocks.STONE);
        if(gate.axis()==Direction.Axis.Z)p.setPos(gate.origin().getX()+0.45,gate.origin().getY()+1.25,gate.origin().getZ()+1.75);
        else p.setPos(gate.origin().getX()+1.75,gate.origin().getY()+1.25,gate.origin().getZ()+0.45);
    }
    private void exactCoordinates(MinecraftServer server,ServerPlayer p) throws Exception {
        ServerLevel home=server.overworld(),target=server.getLevel(WorldDimensions.OVERWORLD);
        var from=new WorldPortals.Frame(new BlockPos(220,179,64),Direction.Axis.X,1);
        prepare(p,home,from);Vec3 start=p.position();
        for(var pos:footprint(target,from).keySet())target.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        var sentinel=from.normal(-1,2,1);target.setBlock(sentinel,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
        var nearby=new WorldPortals.Frame(from.origin().offset(8,7,0),Direction.Axis.X,0);active(target,nearby,Blocks.QUARTZ_BLOCK);
        var beforeNearby=footprint(target,nearby);
        var links=PortalLinks.get(server);links.pair(from.key(home),from.tag(),nearby.key(target),nearby.tag());
        var trip=WorldPortals.OVERWORLD.get().getPortalDestination(home,p,from.at(1,1));
        check("obstructed target stays exact XYZ",trip!=null&&trip.pos().equals(start));
        var endpoint=links.endpoint(from.key(home));
        check("old shifted pair replaced by exact XYZ",BlockPos.of(endpoint.getLong("pos")).equals(from.origin()));
        check("nearby existing portal terrain preserved",beforeNearby.equals(footprint(target,nearby)));
        check("obsolete reciprocal link detached",links.endpoint(nearby.key(target)).isEmpty());
        check("outside minimal footprint unchanged",target.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK));
        check("exact passage only needs 36 blocks",footprint(target,from).size()==36);
        check("minimal entry obstacle cleared",target.isEmptyBlock(from.normal(1,1,-1)));
        p.changeDimension(trip);
        check("fractional player position survives forward trip",p.position().distanceTo(start)<1e-9);
        var back=WorldPortals.HOME.get().getPortalDestination(target,p,from.at(1,1));
        check("exact paired reverse position",back!=null&&back.newLevel()==home&&back.pos().equals(start));
        p.changeDimension(back);
        check("fractional XYZ roundtrip",p.position().distanceTo(start)<1e-9&&p.level()==home);

        var zgate=new WorldPortals.Frame(new BlockPos(240,100,80),Direction.Axis.Z,1);prepare(p,home,zgate);start=p.position();
        trip=WorldPortals.OVERWORLD.get().getPortalDestination(home,p,zgate.at(1,1));
        check("Z axis exact player coordinates",trip!=null&&trip.pos().equals(start));
        check("Z axis target frame orientation preserved",target.getBlockState(zgate.at(1,1)).getValue(NetherPortalBlock.AXIS)==Direction.Axis.Z);

        var blocked=new WorldPortals.Frame(new BlockPos(280,179,80),Direction.Axis.X,1);prepare(p,home,blocked);
        for(var pos:footprint(target,blocked).keySet())target.setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        var original=footprint(target,blocked);veto=true;
        try {check("protection cancels exact portal",WorldPortals.OVERWORLD.get().getPortalDestination(home,p,blocked.at(1,1))==null);}
        finally {veto=false;}
        check("protected terrain rolled back completely",original.equals(footprint(target,blocked)));
        check("protected failure does not create link",links.endpoint(blocked.key(home)).isEmpty());

        var container=new WorldPortals.Frame(new BlockPos(300,179,80),Direction.Axis.X,1);prepare(p,home,container);
        var chest=container.normal(1,1,-1);target.setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
        var chestEntity=(net.minecraft.world.level.block.entity.ChestBlockEntity)target.getBlockEntity(chest);
        chestEntity.setItem(0,new ItemStack(Items.DIAMOND,7));original=footprint(target,container);
        check("container obstruction fails without relocation",WorldPortals.OVERWORLD.get().getPortalDestination(home,p,container.at(1,1))==null);
        check("obstructing container and inventory preserved",original.equals(footprint(target,container))&&chestEntity.getItem(0).getCount()==7);

        var bedrock=new WorldPortals.Frame(new BlockPos(320,179,80),Direction.Axis.X,1);prepare(p,home,bedrock);
        target.setBlock(bedrock.at(1,1),Blocks.BEDROCK.defaultBlockState(),18);original=footprint(target,bedrock);
        check("unbreakable obstacle explicitly fails",WorldPortals.OVERWORLD.get().getPortalDestination(home,p,bedrock.at(1,1))==null);
        check("unbreakable failure has no partial writes",original.equals(footprint(target,bedrock)));

        var exact=WorldPortals.class.getDeclaredMethod("buildExactExit",ServerLevel.class,ServerPlayer.class,WorldPortals.Frame.class,int.class);exact.setAccessible(true);
        for(int y:List.of(target.getMinBuildHeight(),target.getMaxBuildHeight()-5)) {
            var edge=new WorldPortals.Frame(new BlockPos(360,y,96),Direction.Axis.X,1);prepare(p,home,edge);
            for(var pos:footprint(target,edge).keySet())target.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
            var result=(WorldPortals.Frame)exact.invoke(null,target,p,edge,0);
            check("legal height boundary preserves Y "+y,result!=null&&result.origin().equals(edge.origin()));
        }
        for(int y:List.of(target.getMinBuildHeight()-1,target.getMaxBuildHeight()-4)) {
            var edge=new WorldPortals.Frame(new BlockPos(380,y,96),Direction.Axis.X,1);original=footprint(target,edge);
            check("illegal height fails instead of surface search "+y,exact.invoke(null,target,p,edge,0)==null);
            check("height failure has no writes "+y,original.equals(footprint(target,edge)));
        }
        var borderGate=new WorldPortals.Frame(new BlockPos(400,179,80),Direction.Axis.X,1);prepare(p,home,borderGate);
        var border=target.getWorldBorder();double oldSize=border.getSize();original=footprint(target,borderGate);
        try {border.setSize(32);check("border fails without choosing a nearby gate",exact.invoke(null,target,p,borderGate,0)==null);}
        finally {border.setSize(oldSize);}
        check("border failure has no writes",original.equals(footprint(target,borderGate)));
        p.teleportTo(home,0.5,180,2.5,0,0);
    }
    private void homeAdventurePortals(MinecraftServer server,ServerPlayer p,java.lang.reflect.Method tfCheck) throws Exception {
        var home=server.overworld();
        var netherLevel=server.getLevel(Level.NETHER);
        check("vanilla home to Nether coordinate scale remains 1:8",
            net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(home.dimensionType(),netherLevel.dimensionType())==0.125);
        check("vanilla Nether to home coordinate scale remains 8:1",
            net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(netherLevel.dimensionType(),home.dimensionType())==8.0);
        var netherFrame=new WorldPortals.Frame(new BlockPos(120,179,0),Direction.Axis.X,1);
        frame(home,netherFrame,Blocks.OBSIDIAN);
        home.setBlockAndUpdate(netherFrame.at(1,1),Blocks.FIRE.defaultBlockState());
        check("home obsidian frame actually lights",home.getBlockState(netherFrame.at(1,1)).is(Blocks.NETHER_PORTAL));
        p.setPos(121.5,180,0.5);
        var nether=((Portal)Blocks.NETHER_PORTAL).getPortalDestination(home,p,netherFrame.at(1,1));
        p.changeDimension(nether);
        check("home Nether portal really enters Nether",p.level().dimension().equals(Level.NETHER));
        var back=((Portal)Blocks.NETHER_PORTAL).getPortalDestination(p.serverLevel(),p,p.blockPosition());p.changeDimension(back);
        check("Nether return still reaches home",p.level()==home);
        BlockPos endBase=new BlockPos(160,179,0);
        for(int i=0;i<3;i++) {
            home.setBlockAndUpdate(endBase.offset(i,0,-1),Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING,Direction.SOUTH).setValue(EndPortalFrameBlock.HAS_EYE,true));
            home.setBlockAndUpdate(endBase.offset(i,0,3),Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING,Direction.NORTH).setValue(EndPortalFrameBlock.HAS_EYE,true));
            home.setBlockAndUpdate(endBase.offset(-1,0,i),Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING,Direction.EAST).setValue(EndPortalFrameBlock.HAS_EYE,true));
            home.setBlockAndUpdate(endBase.offset(3,0,i),Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING,Direction.WEST).setValue(EndPortalFrameBlock.HAS_EYE,true));
        }
        BlockPos last=endBase.offset(1,0,-1);home.setBlockAndUpdate(last,home.getBlockState(last).setValue(EndPortalFrameBlock.HAS_EYE,false));
        p.setPos(161.5,180,-1.5);p.getInventory().setItem(0,new ItemStack(Items.ENDER_EYE));
        Items.ENDER_EYE.useOn(new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(last),Direction.UP,last,false)));
        check("home End frame actually activates with eyes",home.getBlockState(endBase).is(Blocks.END_PORTAL));
        var end=((Portal)Blocks.END_PORTAL).getPortalDestination(home,p,endBase);p.changeDimension(end);
        check("home End portal really enters End",p.level().dimension().equals(Level.END));
        p.seenCredits=true;
        var endBack=((Portal)Blocks.END_PORTAL).getPortalDestination(p.serverLevel(),p,p.blockPosition());p.changeDimension(endBack);
        check("End exit still reaches home",p.level()==home);
        BlockPos water=new BlockPos(200,179,0);
        for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++) {
            home.setBlockAndUpdate(water.offset(x,-1,z),Blocks.DIRT.defaultBlockState());
            boolean inner=x>=0&&x<=1&&z>=0&&z<=1;
            home.setBlockAndUpdate(water.offset(x,0,z),(inner?Blocks.WATER:Blocks.GRASS_BLOCK).defaultBlockState());
            home.setBlockAndUpdate(water.offset(x,1,z),(inner?Blocks.AIR:Blocks.DANDELION).defaultBlockState());
        }
        p.setPos(200.5,181,0.5);
        var diamond=new ItemEntity(home,200.5,179.5,0.5,new ItemStack(Items.DIAMOND));diamond.setThrower(p);home.addFreshEntity(diamond);
        check("Twilight fixture diamond owner",diamond.getOwner()==p);
        check("Twilight fixture diamond activator",diamond.getItem().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse("twilightforest:portal/activator"))));
        check("Twilight fixture diamond visible",home.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(8)).contains(diamond));
        tfCheck.invoke(null,p,home,8f);
        Block tfPortal=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("twilightforest:twilight_portal"));
        check("home flower pool actually activates with thrown diamond",home.getBlockState(water).is(tfPortal)&&!home.getBlockState(water).is(Blocks.AIR));
        var twilight=((Portal)tfPortal).getPortalDestination(home,p,water);p.changeDimension(twilight);
        check("home Twilight portal really enters forest",p.level().dimension().location().toString().equals("twilightforest:twilight_forest"));
        var twilightBack=((Portal)tfPortal).getPortalDestination(p.serverLevel(),p,p.blockPosition());p.changeDimension(twilightBack);
        check("Twilight return still reaches home",p.level()==home);
    }
    private void tick(ServerTickEvent.Post e) {
        if(done)return;
        if(++ticks==1) {
            e.getServer().overworld().setChunkForced(12,0,true);
            for(var key:WorldDimensions.EXPLORATION)e.getServer().getLevel(key).setChunkForced(0,0,true);
        }
        if(ticks<60)return;done=true;var result=new LinkedHashMap<String,Object>();
        try{exercise(e.getServer());result.put("success",true);}catch(Throwable error){error.printStackTrace();result.put("success",false);result.put("error",error.toString());}
        result.put("passed",passed);
        try{Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){error.printStackTrace();}
        e.getServer().halt(false);
    }
}
