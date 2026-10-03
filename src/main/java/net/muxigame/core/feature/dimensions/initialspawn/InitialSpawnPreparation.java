package net.muxigame.core.feature.dimensions.initialspawn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.muxigame.core.feature.dimensions.DimensionsFeature;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.muxigame.core.mixin.InitialSpawnChunkInvoker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One connection-owned preparation, never a persistent account/world cache. All state is server-thread confined. */
@EventBusSubscriber(modid = "muxi_game_core")
public final class InitialSpawnPreparation {
    static final Component UNAVAILABLE = Component.literal("首次生存落点尚未准备完成，连接已安全结束。请稍后重试；持续失败请联系管理员。");
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/initial-spawn");
    private static final int MAX_PENDING = 16, MAX_ACTIVE = 2;
    private static final long TIMEOUT_NS = TimeUnit.SECONDS.toNanos(90);
    private static final TicketType<Long> TICKET = TicketType.create("muxi_initial_spawn", Long::compare);
    private static final Map<MinecraftServer, Manager> MANAGERS = new IdentityHashMap<>();
    private static long nextRequest;
    private InitialSpawnPreparation() {}

    static void begin(MinecraftServer server, ServerConfigurationPacketListenerImpl listener) {
        if (!server.isSameThread()) throw new IllegalStateException("Initial spawn preparation must run on the server thread");
        Connection connection = listener.getConnection();
        if (!connection.isConnected() || connection.getPacketListener() != listener) return;
        if (server.isStopped() || !server.isRunning()) { listener.disconnect(UNAVAILABLE); return; }
        Manager manager = MANAGERS.computeIfAbsent(server, Manager::new);
        manager.begin(listener);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Manager manager = MANAGERS.get(event.getServer());
        if (manager != null) manager.tick();
    }
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Manager manager = MANAGERS.get(event.getServer());
        if (manager != null) manager.close();
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        Manager manager = MANAGERS.remove(event.getServer());
        if (manager != null) manager.close();
    }

    /** Called after the real native load/events, before any game login packet. No generation fallback. */
    public static boolean apply(Connection connection, ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!server.isSameThread()) throw new IllegalStateException("Initial spawn application must run on the server thread");
        if (!DimensionsFeature.needsInitialSurvival(player)) {
            release(server, connection);
            return true;
        }
        Manager manager = MANAGERS.get(server);
        Request request = manager == null ? null : manager.requests.get(connection);
        try {
            if (request != null && !request.consumed && request.finished && !request.expired()
                    && connection.isConnected() && request.id.equals(player.getUUID())
                    && request.driver != null && player.serverLevel() == request.driver.level
                    && player.serverLevel().dimension().equals(WorldDimensions.OVERWORLD)
                    && request.search.readyNow()) {
                InitialSpawnSearch.Landing landing = request.search.landing();
                Integer height = request.driver.safeHeight(landing.x(), landing.z());
                if (height != null && height == landing.y()) {
                    DimensionsFeature.applyInitialSurvivalSpawn(player, new BlockPos(landing.x(), landing.y(), landing.z()));
                    request.consumed = true;
                    return true; // hold tickets until placeNewPlayer RETURN or cleanup on a later tick
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("Initial spawn final validation failed: {}", failure.getClass().getSimpleName());
        }
        release(server, connection);
        LOG.warn("Refusing initial spawn without a current prepared landing; native load may have changed the prediction");
        connection.disconnect(UNAVAILABLE);
        return false;
    }

    public static void release(MinecraftServer server, Connection connection) {
        Manager manager = MANAGERS.get(server);
        if (manager != null) manager.remove(connection);
    }

    private static final class Request {
        final ServerConfigurationPacketListenerImpl listener;
        final Connection connection;
        final UUID id;
        final long ticket = ++nextRequest;
        final long started = System.nanoTime();
        CompletableFuture<Boolean> prediction;
        Driver driver;
        InitialSpawnSearch search;
        boolean finished, consumed;
        Request(ServerConfigurationPacketListenerImpl listener) {
            this.listener = listener;
            connection = listener.getConnection();
            id = listener.getOwner().getId();
        }
        boolean expired() { return System.nanoTime() - started >= TIMEOUT_NS; }
        boolean current() { return connection.isConnected() && connection.getPacketListener() == listener; }
        void close() {
            // prediction is private IO; generation futures are shared and must not be cancelled.
            if (prediction != null) prediction.cancel(false);
            if (search != null) search.close();
        }
    }

    private static final class Manager implements AutoCloseable {
        final MinecraftServer server;
        final Map<Connection, Request> requests = new IdentityHashMap<>();
        final ThreadPoolExecutor reader = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING), runnable -> {
                Thread thread = new Thread(runnable, "muxi-initial-spawn-nbt");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        boolean closed;
        int cursor;
        Manager(MinecraftServer server) { this.server = server; }

        void begin(ServerConfigurationPacketListenerImpl listener) {
            Connection connection = listener.getConnection();
            if (closed || requests.size() >= MAX_PENDING || requests.containsKey(connection)
                    || listener.getOwner() == null || listener.getOwner().getId() == null) {
                listener.disconnect(UNAVAILABLE);
                return;
            }
            // Do not replace another connection's work or kick an existing in-world session.
            UUID id = listener.getOwner().getId();
            if (server.getPlayerList().getPlayer(id) != null
                    || requests.values().stream().anyMatch(request -> request.id.equals(id) && request.current())) {
                listener.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
                return;
            }
            Request request = new Request(listener);
            requests.put(connection, request);
            try {
                int version = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
                var ownerTag = server.getWorldData().getLoadedPlayerTag();
                if (server.isSingleplayerOwner(listener.getOwner()) && ownerTag != null) {
                    // Only the boolean leaves this thread; never hand a live world tag to the reader.
                    request.prediction = CompletableFuture.completedFuture(InitialSpawnPrediction.existingLoadedOwner(ownerTag));
                } else {
                    Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
                    request.prediction = CompletableFuture.supplyAsync(() -> InitialSpawnPrediction.readExisting(file, version), reader);
                }
            } catch (RuntimeException failure) {
                fail(request, "cannot queue bounded NBT prediction");
            }
        }

        void tick() {
            if (closed) return;
            List<Request> snapshot = new ArrayList<>(requests.values());
            // Remove disconnected/expired requests before counting generation slots.
            for (Request request : snapshot) {
                if (!request.current()) remove(request.connection);
                else if (request.expired()) fail(request, "preparation deadline exceeded");
            }
            snapshot = new ArrayList<>(requests.values());
            if (snapshot.isEmpty()) return;
            int active = (int)snapshot.stream().filter(request -> request.search != null).count();
            int checkBudget = 2;
            long deadlineNs = System.nanoTime() + 1_000_000L;
            int schedulingBudget = 1; // getChunkFutureMainThread may run distance-manager updates synchronously
            int first = Math.floorMod(cursor++, snapshot.size());
            for (int index = 0; index < snapshot.size(); index++) {
                if (System.nanoTime() - deadlineNs >= 0) break;
                Request request = snapshot.get((first + index) % snapshot.size());
                if (request.finished || requests.get(request.connection) != request) continue;
                try {
                    if (!request.prediction.isDone()) continue;
                    if (Boolean.TRUE.equals(request.prediction.getNow(false))) {
                        // No world preparation for confidently existing players. Original load still runs later.
                        remove(request.connection);
                        request.listener.finishCurrentTask(InitialSpawnConfigurationTasks.TYPE);
                        continue;
                    }
                    if (request.search == null) {
                        if (active >= MAX_ACTIVE) continue;
                        ServerLevel level = server.getLevel(WorldDimensions.OVERWORLD);
                        if (level == null) { fail(request, "survival dimension unavailable"); continue; }
                        request.driver = new Driver(level, request.ticket);
                        RandomSource random = RandomSource.create();
                        request.search = new InitialSpawnSearch(random::nextDouble, request.driver);
                        active++;
                    }
                    var progress = request.search.step(schedulingBudget, checkBudget, deadlineNs);
                    schedulingBudget -= progress.scheduled();
                    checkBudget -= progress.inspected();
                    switch (progress.status()) {
                        case READY -> {
                            request.finished = true;
                            request.listener.finishCurrentTask(InitialSpawnConfigurationTasks.TYPE);
                        }
                        case EXHAUSTED -> fail(request, "no safe location in 24 candidates");
                        case FAILED -> fail(request, "chunk preparation failed");
                        case WAITING -> { }
                    }
                } catch (RuntimeException | LinkageError failure) {
                    fail(request, "preparation failed: " + failure.getClass().getSimpleName());
                }
            }
        }

        void fail(Request request, String reason) {
            remove(request.connection);
            LOG.warn("Initial spawn preparation ended: {}", reason);
            if (request.connection.isConnected()) request.listener.disconnect(UNAVAILABLE);
        }
        void remove(Connection connection) {
            Request request = requests.remove(connection);
            if (request != null) {
                try { request.close(); }
                catch (RuntimeException failure) { LOG.warn("Initial spawn ticket cleanup failed", failure); }
            }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            for (Connection connection : List.copyOf(requests.keySet())) remove(connection);
            reader.shutdownNow();
        }
    }

    private static final class Driver implements InitialSpawnSearch.Chunks {
        final ServerLevel level;
        final long ticket;
        final List<ChunkPos> held = new ArrayList<>(4);
        Driver(ServerLevel level, long ticket) { this.level = level; this.ticket = ticket; }
        @Override public boolean inBounds(int x, int z) {
            return level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z));
        }
        @Override public CompletableFuture<Boolean> requestFull(int x, int z) {
            var source = level.getChunkSource();
            ChunkPos pos = new ChunkPos(x, z);
            held.add(pos);
            // Region distance 0 = FULL level 33. This ticket outlives the native UNKNOWN timeout.
            source.addRegionTicket(TICKET, pos, 0, ticket);
            return ((InitialSpawnChunkInvoker)(Object)source)
                .muxi$scheduleInitialSpawnChunk(x, z, ChunkStatus.FULL, true)
                .thenApply(result -> result != null && result.isSuccess() && result.orElse(null) != null);
        }
        @Override public boolean readyNow(int x, int z) {
            return level.getChunkSource().getChunkNow(x, z) != null;
        }
        @Override public Integer safeHeight(int x, int z) {
            if (!inBounds(x, z)) return null;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return DimensionsFeature.safeInitialSpawn(level, new BlockPos(x, y, z)) ? y : null;
        }
        @Override public void release() {
            RuntimeException first = null;
            for (ChunkPos pos : held) {
                try { level.getChunkSource().removeRegionTicket(TICKET, pos, 0, ticket); }
                catch (RuntimeException failure) { if (first == null) first = failure; else first.addSuppressed(failure); }
            }
            held.clear();
            if (first != null) throw first;
        }
    }
}
