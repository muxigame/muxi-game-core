package net.muxigame.core.client.waystones;

import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.waystones.WaystoneMapNetwork;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lightweight client state used by Xaero compatibility mixins. */
public final class WaystoneMapClient {
    private static final int SOURCE_RADIUS=4;
    private static int checkedTick=Integer.MIN_VALUE;
    private static boolean nearSource;
    private WaystoneMapClient() {}

    public static boolean isWaystoneOrigin(ResourceLocation origin) {
        return origin!=null && "waystones".equals(origin.getNamespace());
    }

    public static boolean nearSource() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return false;
        if(checkedTick==mc.player.tickCount) return nearSource;
        checkedTick=mc.player.tickCount;
        BlockPos center=mc.player.blockPosition(); BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        int r=SOURCE_RADIUS; nearSource=false;
        outer: for(int dy=-3;dy<=3;dy++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            if(dx*dx+dy*dy+dz*dz>r*r) continue;
            pos.set(center.getX()+dx,center.getY()+dy,center.getZ()+dz);
            if(mc.level.getBlockState(pos).getBlock() instanceof WaystoneBlockBase) { nearSource=true; break outer; }
        }
        return nearSource;
    }

    public static boolean supported() {
        Minecraft mc=Minecraft.getInstance();
        return mc.getConnection()!=null && NetworkRegistry.hasChannel(mc.getConnection(),WaystoneMapNetwork.Teleport.TYPE.id());
    }

    public static void teleport(ResourceKey<Level> dimension, BlockPos pos) {
        Minecraft mc=Minecraft.getInstance();
        if(!supported()) {
            if(mc.player!=null) mc.player.displayClientMessage(Component.translatable("muxi.map.waystone.server_not_ready"),true);
            return;
        }
        PacketDistributor.sendToServer(new WaystoneMapNetwork.Teleport(dimension.location(),pos));
    }
}
