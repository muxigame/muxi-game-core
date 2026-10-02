package net.muxigame.core.feature.dimensions;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class WorldPortals {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("muxi_game_core");
    public static final DeferredBlock<WorldPortalBlock> HOME=BLOCKS.register("home_portal",()->new WorldPortalBlock(0));
    public static final DeferredBlock<WorldPortalBlock> OVERWORLD=BLOCKS.register("overworld_portal",()->new WorldPortalBlock(1));
    private static final Set<Block> FRAMES=Set.of(Blocks.COBBLESTONE,Blocks.STONE,Blocks.QUARTZ_BLOCK,Blocks.DIRT,Blocks.GRASS_BLOCK);
    private WorldPortals() {}
    public static void register(IEventBus modBus) { BLOCKS.register(modBus); }
    public static void registerEvents(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST,WorldPortals::ignite);
        bus.addListener(WorldPortals::endEye);
        bus.addListener(WorldPortals::adventureTravel);
    }
    private static void endEye(PlayerInteractEvent.RightClickBlock event) {
        if(WorldDimensions.exploration(event.getLevel().dimension()) && event.getItemStack().is(Items.ENDER_EYE)
            && event.getLevel().getBlockState(event.getPos()).is(Blocks.END_PORTAL_FRAME)) {
            event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
            if(event.getEntity() instanceof ServerPlayer player)message(player,"请回家园建造暮色、下界和末地传送门");
        }
    }
    private static void adventureTravel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if(WorldDimensions.exploration(event.getEntity().level().dimension()) && !event.getDimension().equals(Level.OVERWORLD)) {
            event.setCanceled(true);
            if(event.getEntity() instanceof ServerPlayer player && player.tickCount%40==0)
                message(player,"此世界只能返回家园，请先通过家园门返回");
        }
    }
    public static WorldPortalBlock block(int destination) { return destination==0?HOME.get():OVERWORLD.get(); }
    public record Frame(BlockPos origin,Direction.Axis axis,int destination) {
        public BlockPos at(int width,int height) {return axis==Direction.Axis.X?origin.offset(width,height,0):origin.offset(0,height,width);}
        public BlockPos normal(int width,int height,int depth) {return axis==Direction.Axis.X?at(width,height).offset(0,0,depth):at(width,height).offset(depth,0,0);}
        public String key(ServerLevel level) {return level.dimension().location()+"/"+origin.asLong()+"/"+axis.getName();}
        public CompoundTag tag() {var t=new CompoundTag();t.putLong("pos",origin.asLong());t.putString("axis",axis.getName());t.putInt("destination",destination);return t;}
    }
    /** Fixed 4x5 outer frame, including corners. Only air/fire or this gate's portal is allowed inside. */
    public static Frame find(LevelAccessor level,BlockPos touched,int destination,boolean active) {
        for(Direction.Axis axis:List.of(Direction.Axis.X,Direction.Axis.Z))
            for(int y=0;y<5;y++)for(int x=0;x<4;x++) {
                Frame frame=new Frame(axis==Direction.Axis.X?touched.offset(-x,-y,0):touched.offset(0,-y,-x),axis,destination);
                if(valid(level,frame,active))return frame;
            }
        return null;
    }
    public static boolean valid(LevelAccessor level,Frame frame,boolean active) {
        for(int x=0;x<4;x++)for(int y=0;y<5;y++) {
            var state=level.getBlockState(frame.at(x,y));
            if(x==0||x==3||y==0||y==4) {
                // Stacked grass naturally becomes dirt; that must not destroy a working gate.
                if(!FRAMES.contains(state.getBlock()))return false;
            }
            else if(active) {if(!state.is(block(frame.destination)))return false;}
            else if(!(state.isAir()||state.is(Blocks.FIRE)))return false;
        }
        return true;
    }
    private static void ignite(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||!event.getItemStack().is(Items.FLINT_AND_STEEL))return;
        Block material=event.getLevel().getBlockState(event.getPos()).getBlock();
        if(!FRAMES.contains(material)||!WorldDimensions.managed(player.level().dimension()))return;
        int destination=player.level().dimension().equals(Level.OVERWORLD)?1:0;
        Frame frame=find(player.serverLevel(),event.getPos(),destination,false);
        if(frame==null)return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(WorldDimensions.exploration(player.level().dimension())&&destination!=0) {message(player,"此世界只能建造返回家园的门");return;}
        if(player.level().dimension().equals(WorldDimensions.ALL.get(destination).key())) {message(player,"这个门框指向当前世界，请选择其他目的地的门框");return;}
        if(!canTravel(player))return;
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        for(int x=1;x<=2;x++)for(int y=1;y<=3;y++)changes.put(frame.at(x,y),block(destination).defaultBlockState().setValue(NetherPortalBlock.AXIS,frame.axis));
        if(!place(player.serverLevel(),player,changes)) {message(player,"当前位置的保护规则阻止了开启传送门");return;}
        event.getItemStack().hurtAndBreak(1,player,net.minecraft.world.entity.LivingEntity.getSlotForHand(event.getHand()));
        message(player,"通往"+WorldDimensions.ALL.get(destination).name()+"的门已开启，走入门内即可传送");
    }
    private static boolean canTravel(ServerPlayer player) {
        if(!player.isAlive()||player.isSleeping()||player.isPassenger()||player.isVehicle()
            ||net.muxigame.minigames.GameRuntime.blocksWorldTravel(player))return false;
        return player.containerMenu==player.inventoryMenu&&player.containerMenu.getCarried().isEmpty();
    }
    private static void message(ServerPlayer player,String text) {player.displayClientMessage(Component.literal(text),true);}
    public static DimensionTransition transition(ServerLevel source,Entity entity,BlockPos pos,WorldPortalBlock portal) {
        if(!(entity instanceof ServerPlayer player)||!canTravel(player)||!WorldDimensions.managed(source.dimension()))return null;
        int sourceIndex=-1;
        for(int i=0;i<WorldDimensions.ALL.size();i++)if(WorldDimensions.ALL.get(i).key().equals(source.dimension()))sourceIndex=i;
        if(sourceIndex==portal.destination())return null;
        if(sourceIndex!=0&&portal.destination()!=0)return null;
        Frame from=find(source,pos,portal.destination(),true);
        if(from==null)return null;
        ServerLevel target=source.getServer().getLevel(WorldDimensions.ALL.get(portal.destination()).key());
        if(target==null)return null;
        PortalLinks links=PortalLinks.get(source.getServer());
        boolean exactPair=(sourceIndex==0&&portal.destination()==1)||(sourceIndex==1&&portal.destination()==0);
        if(exactPair) {
            Frame to=buildExactExit(target,player,from,sourceIndex);
            if(to==null) {
                message(player,"同坐标门无法建立：门体或出入口越界、受保护，或含方块实体/不可破坏障碍；未迁移门的位置");
                return null;
            }
            // Old surface/nearby links are replaced only after the exact placement succeeds.
            CompoundTag oldTo=links.endpoint(from.key(source)),oldFrom=links.endpoint(to.key(target));
            links.pairReplacing(from.key(source),from.tag(),to.key(target),to.tag(),
                endpointKey(target,oldTo),endpointKey(source,oldFrom));
            return arrival(target,player,player.position());
        }
        CompoundTag tag=links.endpoint(from.key(source));
        Frame to=null;
        if(tag.contains("pos")) {
            to=new Frame(BlockPos.of(tag.getLong("pos")),tag.getString("axis").equals("x")?Direction.Axis.X:Direction.Axis.Z,sourceIndex);
            if(!valid(target,to,true)) {message(player,"配对的回程门已损坏，请先修复原门框并重新点火");return null;}
        }
        if(to==null) {
            to=buildExit(target,player,from.origin,sourceIndex);
            if(to==null) {message(player,"目标区域没有可建门的空地，或被领地保护；请换个位置建门");return null;}
            links.pair(from.key(source),from.tag(),to.key(target),to.tag());
        }
        // Arrival inside the paired gate is safe. Vanilla's portal cooldown prevents immediate bounce-back.
        BlockPos landing=to.at(1,1);
        return arrival(target,player,Vec3.atBottomCenterOf(landing));
    }
    private static DimensionTransition arrival(ServerLevel target,ServerPlayer player,Vec3 position) {
        return new DimensionTransition(target,position,Vec3.ZERO,player.getYRot(),player.getXRot(),
            DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(arrived->{
                arrived.setPortalCooldown(40);
                if(arrived instanceof ServerPlayer p)message(p,"已抵达"+WorldDimensions.name(target.dimension()));
            }));
    }
    private static String endpointKey(ServerLevel level,CompoundTag endpoint) {
        if(!endpoint.contains("pos"))return null;
        return new Frame(BlockPos.of(endpoint.getLong("pos")),
            endpoint.getString("axis").equals("x")?Direction.Axis.X:Direction.Axis.Z,0).key(level);
    }
    /** Home/survival gates keep the exact XYZ anchor, axis and player fractional position. */
    private static Frame buildExactExit(ServerLevel level,ServerPlayer player,Frame from,int destination) {
        Frame frame=new Frame(from.origin,from.axis,destination);
        for(int w=0;w<4;w++)for(int h=0;h<5;h++)
            if(level.isOutsideBuildHeight(frame.at(w,h))||!level.getWorldBorder().isWithinBounds(frame.at(w,h)))return null;
        for(int w=1;w<=2;w++)for(int d:List.of(-1,1))for(int h=0;h<=3;h++)
            if(level.isOutsideBuildHeight(frame.normal(w,h,d))||!level.getWorldBorder().isWithinBounds(frame.normal(w,h,d)))return null;
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        boolean existing=valid(level,frame,true);
        for(int w=0;w<4;w++)for(int h=0;h<5;h++) {
            boolean border=w==0||w==3||h==0||h==4;
            changes.put(frame.at(w,h),existing&&border?level.getBlockState(frame.at(w,h)):
                border?Blocks.COBBLESTONE.defaultBlockState():block(destination).defaultBlockState().setValue(NetherPortalBlock.AXIS,frame.axis));
        }
        // Only the two portal columns and one block immediately in front/behind.
        for(int w=1;w<=2;w++)for(int d:List.of(-1,1)) {
            BlockPos floor=frame.normal(w,0,d);
            BlockState state=level.getBlockState(floor);
            if(!state.isCollisionShapeFullBlock(level,floor)||state.is(Blocks.MAGMA_BLOCK))
                changes.put(floor,Blocks.STONE_BRICKS.defaultBlockState());
            for(int h=1;h<=3;h++)changes.put(frame.normal(w,h,d),Blocks.AIR.defaultBlockState());
        }
        changes.entrySet().removeIf(entry->level.getBlockState(entry.getKey()).equals(entry.getValue()));
        for(var entry:changes.entrySet()) {
            BlockPos pos=entry.getKey();BlockState state=level.getBlockState(pos);
            if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)
                ||(state.getDestroySpeed(level,pos)<0&&!state.is(HOME.get())&&!state.is(OVERWORLD.get()))
                ||level.getBlockEntity(pos)!=null)return null;
        }
        if(!placeExact(level,player,changes))return null;
        return frame;
    }
    private static boolean placeExact(ServerLevel level,ServerPlayer player,Map<BlockPos,BlockState> changes) {
        List<BlockSnapshot> snapshots=new ArrayList<>();
        for(var entry:changes.entrySet()) {
            if(!level.mayInteract(player,entry.getKey()))return false;
            snapshots.add(BlockSnapshot.create(level.dimension(),level,entry.getKey()));
        }
        boolean failed=false;
        try {
            for(var entry:changes.entrySet()) {
                level.setBlock(entry.getKey(),entry.getValue(),18);
                if(!level.getBlockState(entry.getKey()).equals(entry.getValue())) {failed=true;break;}
            }
            if(!failed&&( !level.noCollision(player,player.getBoundingBox())
                ||(!snapshots.isEmpty()&&EventHooks.onMultiBlockPlace(player,snapshots,Direction.UP))))failed=true;
        } catch(RuntimeException error) {
            for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(18);
            throw error;
        }
        if(failed) {
            for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(18);
            return false;
        }
        for(var entry:changes.entrySet())level.updateNeighborsAt(entry.getKey(),entry.getValue().getBlock());
        return true;
    }
    private static Frame buildExit(ServerLevel level,ServerPlayer player,BlockPos center,int destination) {
        for(int radius=0;radius<=32;radius+=8)for(int dx=-radius;dx<=radius;dx+=8)for(int dz=-radius;dz<=radius;dz+=8) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int x=center.getX()+dx,z=center.getZ()+dz,y=level.getMinBuildHeight()+1;
            if(!level.getWorldBorder().isWithinBounds(new BlockPos(x,0,z)))continue;
            for(int w=-1;w<=4;w++)for(int d=-1;d<=1;d++)y=Math.max(y,level.getHeight(Heightmap.Types.MOTION_BLOCKING,x+w,z+d));
            Frame frame=new Frame(new BlockPos(x,y,z),Direction.Axis.X,destination);
            if(y+5>=level.getMaxBuildHeight())continue;
            Map<BlockPos,BlockState> changes=new LinkedHashMap<>();boolean clear=true;
            for(int w=-1;w<=4;w++)for(int d=-1;d<=1;d++)for(int h=0;h<=4;h++) {
                BlockPos p=frame.normal(w,h,d);
                if(!level.getWorldBorder().isWithinBounds(p)||!level.isEmptyBlock(p)){clear=false;break;}
                if(h==0)changes.put(p,Blocks.STONE_BRICKS.defaultBlockState());
            }
            if(!clear)continue;
            for(int w=0;w<4;w++)for(int h=0;h<5;h++)changes.put(frame.at(w,h),
                w==0||w==3||h==0||h==4?Blocks.COBBLESTONE.defaultBlockState():block(destination).defaultBlockState().setValue(NetherPortalBlock.AXIS,frame.axis));
            if(place(level,player,changes))return frame;
        }
        return null;
    }
    /** Snapshot placement gives claim/protection mods their normal veto and rolls back on cancellation. */
    private static boolean place(ServerLevel level,ServerPlayer player,Map<BlockPos,BlockState> changes) {
        List<BlockSnapshot> snapshots=new ArrayList<>();
        for(var entry:changes.entrySet()) {
            if(!level.getWorldBorder().isWithinBounds(entry.getKey())||!level.mayInteract(player,entry.getKey()))return false;
            snapshots.add(BlockSnapshot.create(level.dimension(),level,entry.getKey()));
        }
        for(var entry:changes.entrySet())level.setBlock(entry.getKey(),entry.getValue(),18);
        if(EventHooks.onMultiBlockPlace(player,snapshots,Direction.UP)) {
            for(int i=snapshots.size()-1;i>=0;i--)snapshots.get(i).restore(18);
            return false;
        }
        for(var entry:changes.entrySet())level.updateNeighborsAt(entry.getKey(),entry.getValue().getBlock());
        return true;
    }
}
