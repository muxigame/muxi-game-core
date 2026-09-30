package net.muxigame.core.feature.waystones;

import com.mojang.datafixers.util.Either;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.Optional;

/** Server-authoritative teleport bridge between Xaero map markers and Waystones. */
public final class WaystoneMapNetwork {
    private static final int SOURCE_RADIUS = 4;
    private WaystoneMapNetwork() {}

    public record Teleport(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
        public static final Type<Teleport> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("muxi_game_core", "waystone_map_teleport_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Teleport> CODEC = StreamCodec.of(
            (b,p)->{ b.writeResourceLocation(p.dimension); b.writeBlockPos(p.pos); },
            b->new Teleport(b.readResourceLocation(), b.readBlockPos()));
        @Override public Type<Teleport> type() { return TYPE; }
    }

    public static void register(IEventBus modBus) { modBus.addListener(WaystoneMapNetwork::registerPayloads); }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("waystone-map-1").optional().playToServer(Teleport.TYPE, Teleport.CODEC, (packet,context)-> {
            if(context.player() instanceof ServerPlayer player) handle(player, packet);
        });
    }

    private static void handle(ServerPlayer player, Teleport packet) {
        ServerLevel targetLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, packet.dimension));
        if(targetLevel == null) { deny(player,"muxi.map.waystone.invalid_target"); return; }
        Optional<Waystone> targetOpt = WaystonesAPI.getWaystoneAt(targetLevel, packet.pos);
        if(targetOpt.isEmpty()) { deny(player,"muxi.map.waystone.invalid_target"); return; }
        Waystone target = targetOpt.get();
        if(!target.isValid()) { deny(player,"muxi.map.waystone.invalid_target"); return; }

        boolean shared = WaystoneTypes.isSharestone(target.getWaystoneType());
        if(!shared && !WaystonesAPI.isWaystoneActivated(player, target)) {
            deny(player,"muxi.map.waystone.not_activated"); return;
        }

        Waystone source = nearbySource(player);
        if(source == null) { deny(player,"muxi.map.waystone.need_source"); return; }

        Either<WaystoneTeleportContext, WaystoneTeleportError> prepared =
            WaystonesAPI.createDefaultTeleportContext(player, target, ctx->ctx.setFromWaystone(source));
        prepared.ifRight(error->player.displayClientMessage(error.getComponent(), true));
        prepared.ifLeft(ctx->{
            Either<java.util.List<net.minecraft.world.entity.Entity>, WaystoneTeleportError> result = WaystonesAPI.tryTeleport(ctx);
            result.ifRight(error->player.displayClientMessage(error.getComponent(), true));
        });
    }

    /** Any actual Waystones pedestal beside the player can be the source, activated or not. */
    private static Waystone nearbySource(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = SOURCE_RADIUS;
        for(int dy=-3;dy<=3;dy++) for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
            if(dx*dx + dy*dy + dz*dz > r*r) continue;
            pos.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
            if(!(level.getBlockState(pos).getBlock() instanceof WaystoneBlockBase)) continue;
            Optional<Waystone> found = WaystonesAPI.getWaystoneAt(level, pos.immutable());
            if(found.isPresent() && found.get().isValid()) return found.get();
        }
        return null;
    }

    private static void deny(ServerPlayer player,String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
