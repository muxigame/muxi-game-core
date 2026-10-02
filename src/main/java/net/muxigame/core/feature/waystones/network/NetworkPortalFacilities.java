package net.muxigame.core.feature.waystones.network;

import net.blay09.mods.waystones.api.*;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.muxigame.core.feature.dimensions.*;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;

/** Every authority fact is derived from the sender's live server world and the operator file. */
public final class NetworkPortalFacilities {
    private NetworkPortalFacilities() {}
    public record Source(Waystone stone, NetworkFacilityConfig.Facility facility, String frameKey) {}
    public static NetworkFacilityConfig config() {
        return NetworkFacilityConfig.read(FMLPaths.CONFIGDIR.get().resolve("muxi-game-core/teleport-network.json"));
    }
    private static boolean range(BlockPos a,BlockPos b,int radius,int vertical) {
        return NetworkPermissionPolicy.inCandidateRange(a.getX()-b.getX(),a.getY()-b.getY(),a.getZ()-b.getZ(),radius,vertical);
    }
    /** Do not let WorldPortals.find generate/load neighbouring chunks while inspecting a frame. */
    public static WorldPortals.Frame frame(ServerLevel level,BlockPos touched) {
        if(touched.getY()-4<level.getMinBuildHeight() || touched.getY()+4>=level.getMaxBuildHeight()) return null;
        if(!level.hasChunkAt(touched) || !(level.getBlockState(touched).getBlock() instanceof WorldPortalBlock block)) return null;
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) if(!level.hasChunkAt(touched.offset(x,0,z))) return null;
        WorldPortals.Frame frame=WorldPortals.find(level,touched,block.destination(),true);
        if(frame==null || !WorldDimensions.managed(level.dimension()))return null;
        int sourceIndex=-1;
        for(int i=0;i<WorldDimensions.ALL.size();i++)if(WorldDimensions.ALL.get(i).key().equals(level.dimension()))sourceIndex=i;
        // Retain the physical gate's existing direction boundary as well as its actual frame.
        if(sourceIndex<0 || frame.destination()<0 || frame.destination()>=WorldDimensions.ALL.size()
            || sourceIndex==frame.destination() || (sourceIndex!=0 && frame.destination()!=0))return null;
        if(level.getServer().getLevel(WorldDimensions.ALL.get(frame.destination()).key())==null)return null;
        return frame;
    }
    public static boolean actualStone(ServerLevel level,Waystone stone) {
        if(!stone.isValid() || !level.dimension().equals(stone.getDimension()) || !level.hasChunkAt(stone.getPos())) return false;
        if(!(level.getBlockState(stone.getPos()).getBlock() instanceof WaystoneBlockBase)) return false;
        return WaystonesAPI.getWaystoneAt(level,stone.getPos()).filter(s->s.isValid() && s.getWaystoneUid().equals(stone.getWaystoneUid())).isPresent();
    }
    private static boolean besideFrame(WorldPortals.Frame frame,BlockPos pos,int radius,int vertical) {
        for(int x=1;x<=2;x++) for(int y=1;y<=3;y++) if(range(frame.at(x,y),pos,radius,vertical)) return true;
        return false;
    }
    public static Source validate(MinecraftServer server,NetworkFacilityConfig cfg,NetworkFacilityConfig.Facility facility) {
        ResourceLocation dimension=ResourceLocation.tryParse(facility.dimension());
        if(dimension==null) return null;
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,dimension));
        if(level==null || !WorldDimensions.managed(level.dimension())) return null;
        Waystone stone=WaystonesAPI.getWaystone(server,facility.stone()).orElse(null);
        if(stone==null || !actualStone(level,stone)) return null;
        WorldPortals.Frame frame=frame(level,new BlockPos(facility.x(),facility.y(),facility.z()));
        if(frame==null || !besideFrame(frame,stone.getPos(),cfg.associationRadius(),cfg.associationVertical())) return null;
        return new Source(stone,facility,frame.key(level));
    }
    public static boolean currentDimensionGateway(ServerPlayer player,NetworkFacilityConfig cfg) {
        String current=player.serverLevel().dimension().location().toString();
        return cfg.facilities().stream().filter(f->f.dimension().equals(current)).anyMatch(f->validate(player.server,cfg,f)!=null);
    }
    public static Source source(ServerPlayer player,NetworkFacilityConfig cfg) {
        ServerLevel level=player.serverLevel(); BlockPos center=player.blockPosition();
        int r=cfg.entranceRadius();
        for(int y=-cfg.entranceVertical();y<=cfg.entranceVertical();y++) for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++) {
            if(!NetworkPermissionPolicy.inCandidateRange(x,y,z,r,cfg.entranceVertical())) continue;
            BlockPos pos=center.offset(x,y,z); if(!level.hasChunkAt(pos)) continue;
            if(!(level.getBlockState(pos).getBlock() instanceof WaystoneBlockBase)) continue;
            Waystone stone=WaystonesAPI.getWaystoneAt(level,pos).orElse(null);
            if(stone!=null && actualStone(level,stone)) return new Source(stone,null,"");
        }
        for(var f:cfg.facilities()) {
            if(!f.dimension().equals(level.dimension().location().toString())) continue;
            Source source=validate(player.server,cfg,f); if(source==null) continue;
            WorldPortals.Frame frame=frame(level,new BlockPos(f.x(),f.y(),f.z()));
            if(frame!=null && besideFrame(frame,center,cfg.entranceRadius(),cfg.entranceVertical())) return source;
        }
        return null;
    }
    /** Player gates may decorate an existing stone only; they never enter the trusted facility list. */
    public static boolean portalBadge(MinecraftServer server,Waystone stone,NetworkFacilityConfig cfg) {
        ServerLevel level=server.getLevel(stone.getDimension());
        if(level==null || !actualStone(level,stone)) return false;
        if(cfg.facilities().stream().filter(f->f.stone().equals(stone.getWaystoneUid())).anyMatch(f->validate(server,cfg,f)!=null)) return true;
        Set<String> frames=new HashSet<>(); int r=cfg.associationRadius();
        for(int y=-cfg.associationVertical();y<=cfg.associationVertical();y++) for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++) {
            if(!NetworkPermissionPolicy.inCandidateRange(x,y,z,r,cfg.associationVertical())) continue;
            WorldPortals.Frame frame=frame(level,stone.getPos().offset(x,y,z));
            if(frame!=null && besideFrame(frame,stone.getPos(),r,cfg.associationVertical())) frames.add(frame.key(level));
        }
        // An ambiguous unregistered association does not present a connected badge.
        return frames.size()==1;
    }
}
