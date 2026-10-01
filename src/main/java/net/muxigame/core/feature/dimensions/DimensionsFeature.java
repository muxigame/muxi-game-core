package net.muxigame.core.feature.dimensions;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.challenge.ChallengeFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.util.Set;

/** Home and overworld-like survival worlds with shared inventory and administrator recovery commands. */
public final class DimensionsFeature implements ServerFeature {
    public static final String POSITIONS = "muxi_dimension_positions";
    private static final String COOLDOWN = "muxi_dimension_travel_tick";
    private static final String PLAYER_PERSISTED = "PlayerPersisted";
    private static final String FIRST_JOIN_BOOK = "muxiFirstJoinBook";
    private static final String INITIAL_SURVIVAL_PENDING = "muxiInitialSurvivalPending";
    private static final String INITIAL_SURVIVAL_DONE = "muxiInitialSurvivalDone";
    private static final int INITIAL_SURVIVAL_RADIUS = 10_000;
    public String id() { return "dimensions"; }
    public void register(IEventBus bus) {
        WorldPortals.registerEvents(bus);
        bus.addListener(this::commands);
        bus.addListener(this::clonePlayer);
        bus.addListener(this::login);
    }
    public void close() {}
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        // Tick counters reset on restart. A stored cooldown must not lock a player out after a reboot.
        event.getEntity().getPersistentData().remove(COOLDOWN);
    }
    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(PLAYER_PERSISTED);
        root.put(PLAYER_PERSISTED, tag);
        return tag;
    }
    /** Called after player NBT is loaded but before the login packet chooses a world. */
    public static boolean needsInitialSurvival(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (persisted.getBoolean(INITIAL_SURVIVAL_DONE)) return false;
        if (persisted.getBoolean(INITIAL_SURVIVAL_PENDING)) return true;
        // No handbook marker means genuinely new player. Existing players already handled by the
        // old first-join script keep their saved dimension and position.
        return !persisted.getBoolean(FIRST_JOIN_BOOK);
    }
    /** Uniform disk sampling. Each sample explicitly generates one target chunk before height/safety checks. */
    public static BlockPos findInitialSurvivalLanding(ServerLevel target, ServerPlayer player) {
        var random = player.getRandom();
        int[][] offsets = {{0,0},{4,0},{-4,0},{0,4},{0,-4},{4,4},{4,-4},{-4,4},{-4,-4}};
        for (int attempt = 0; attempt < 24; attempt++) {
            double radius = Math.sqrt(random.nextDouble()) * INITIAL_SURVIVAL_RADIUS;
            double angle = random.nextDouble() * Math.PI * 2.0;
            int originX = (int)Math.floor(Math.cos(angle) * radius);
            int originZ = (int)Math.floor(Math.sin(angle) * radius);
            target.getChunk(originX >> 4, originZ >> 4);
            for (int[] offset : offsets) {
                int x = originX + offset[0], z = originZ + offset[1];
                if ((long)x*x + (long)z*z > (long)INITIAL_SURVIVAL_RADIUS*INITIAL_SURVIVAL_RADIUS) continue;
                BlockPos column = new BlockPos(x, 0, z);
                if (!target.getWorldBorder().isWithinBounds(column)) continue;
                int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos landing = new BlockPos(x, y, z);
                if (safeInitialSpawn(target, landing)) return landing;
            }
        }
        return null;
    }
    public static boolean safeInitialSpawn(ServerLevel level, BlockPos pos) {
        if (!safe(level, pos) || level.getBiome(pos).is(BiomeTags.IS_OCEAN)) return false;
        var floor = level.getBlockState(pos.below());
        return !floor.is(BlockTags.LOGS) && !floor.is(Blocks.POWDER_SNOW)
            && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }
    /** Position is applied before ClientboundLoginPacket is constructed, so the client starts directly in survival. */
    public static void applyInitialSurvivalSpawn(ServerPlayer player, BlockPos landing) {
        player.setPos(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        player.setRespawnPosition(WorldDimensions.OVERWORLD, landing, player.getYRot(), true, false);
        CompoundTag persisted = persisted(player);
        persisted.remove(INITIAL_SURVIVAL_PENDING);
        persisted.putBoolean(INITIAL_SURVIVAL_DONE, true);
        player.fallDistance = 0;
    }
    private void clonePlayer(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(POSITIONS, Tag.TAG_COMPOUND))
            event.getEntity().getPersistentData().put(POSITIONS, old.getCompound(POSITIONS).copy());
    }
    private void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("muxiworld").requires(source -> source.hasPermission(2)).executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            player.sendSystemMessage(Component.literal("当前维度：" + WorldDimensions.name(player.level().dimension())));
            for (var destination : WorldDimensions.ALL)
                player.sendSystemMessage(Component.literal("[前往" + destination.name() + "]"
                    + (destination.resettable() ? "（可重置）" : "（长期保留）"))
                    .withStyle(s -> s.withColor(0x83D9AE).withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND, "/muxiworld " + destination.command()))));
            return 1;
        });
        for (var destination : WorldDimensions.ALL)
            root.then(Commands.literal(destination.command()).executes(c -> travel(c.getSource().getPlayerOrException(), destination)));
        event.getDispatcher().register(root);
    }
    private static int reject(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
        return 0;
    }
    public static int travel(ServerPlayer player, WorldDimensions.Destination destination) {
        if (!WorldDimensions.ALL.contains(destination)) return reject(player, "未知的目标维度");
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.isVehicle())
            return reject(player, "请先起床或离开坐骑，再切换维度");
        var challenge = ChallengeFeature.active(player.server);
        if (ChallengeFeature.locked(player) || ChallengeFeature.dimension(player.level())
            || (challenge != null && challenge.room(player.getUUID()) != null))
            return reject(player, "请先离开挑战房间，再切换维度");
        if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty())
            return reject(player, "请先关闭容器并放下鼠标上的物品");
        if (player.level().dimension().equals(destination.key())) return reject(player, "你已在" + destination.name());
        CompoundTag data = player.getPersistentData();
        long now = player.server.overworld().getGameTime();
        if (data.contains(COOLDOWN) && now - data.getLong(COOLDOWN) < 100)
            return reject(player, "维度传送冷却中，请稍候 5 秒");
        ServerLevel target = player.server.getLevel(destination.key());
        if (target == null) return reject(player, "目标维度尚未加载，请联系管理员重启以载入维度");
        CompoundTag positions = data.getCompound(POSITIONS);
        CompoundTag remembered = positions.getCompound(destination.key().location().toString());
        BlockPos center = target.getSharedSpawnPos();
        if (remembered.contains("x") && remembered.contains("y") && remembered.contains("z"))
            center = new BlockPos(remembered.getInt("x"), remembered.getInt("y"), remembered.getInt("z"));
        BlockPos landing = findLanding(target, center);
        if (landing == null && !center.equals(target.getSharedSpawnPos())) landing = findLanding(target, target.getSharedSpawnPos());
        if (landing == null) return reject(player, "附近没有安全落点，请联系管理员设置维度出生点");
        var previousKey = player.level().dimension();
        CompoundTag previous = new CompoundTag();
        previous.putInt("x", player.blockPosition().getX());
        previous.putInt("y", player.blockPosition().getY());
        previous.putInt("z", player.blockPosition().getZ());
        previous.putFloat("yaw", player.getYRot());
        previous.putFloat("pitch", player.getXRot());
        boolean moved = player.teleportTo(target, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5,
            Set.of(), remembered.getFloat("yaw"), remembered.getFloat("pitch"));
        if (!moved || !player.level().dimension().equals(destination.key())) return reject(player, "传送被当前世界规则阻止");
        positions.put(previousKey.location().toString(), previous);
        data.put(POSITIONS, positions);
        data.putLong(COOLDOWN, now);
        player.fallDistance = 0;
        player.sendSystemMessage(Component.literal("已抵达" + destination.name()));
        return 1;
    }
    /** Keep safe saved Y (including homes underground); otherwise search a bounded surface area. No terrain edits. */
    public static BlockPos findLanding(ServerLevel level, BlockPos center) {
        if (safe(level, center)) return center;
        for (int radius = 0; radius <= 32; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos column = new BlockPos(center.getX() + dx, 0, center.getZ() + dz);
                    if (!level.getWorldBorder().isWithinBounds(column)) continue;
                    BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                    if (safe(level, pos)) return pos;
                }
            }
        }
        return null;
    }
    public static boolean safe(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 1 >= level.getMaxBuildHeight()
            || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var floor = level.getBlockState(pos.below());
        if (!floor.isFaceSturdy(level, pos.below(), Direction.UP) || !floor.getFluidState().isEmpty()
            || floor.is(BlockTags.LEAVES) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE)
            || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.CACTUS)) return false;
        // Air-only headroom also rejects fluids, fire, powder snow, portals and damaging plants.
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) return false;
        return level.noCollision(new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
            pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8));
    }
}
