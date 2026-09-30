package net.muxigame.core.threading;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.muxigame.core.threading.mixin.LevelOwnerAccess;
import net.muxigame.core.threading.mixin.ChunkOwnerAccess;
import net.muxigame.core.threading.mixin.ServerTimingAccess;
import net.neoforged.neoforge.event.EventHooks;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Opt-in native adapter. Ownership transfers to workers only during the world-tick barrier. */
public final class DimensionThreads {
    public static final boolean ENABLED = Boolean.getBoolean("muxi.dimensionThreads");
    private static final ConcurrentMap<MinecraftServer, DimensionThreads> SERVERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<State> TICK = new ThreadLocal<>();
    private static final ThreadLocal<State> LOADING = new ThreadLocal<>();
    // JVM processor count is a sizing hint, not a CPU reservation. Keep at least
    // two processors' worth of generation concurrency out of the foreground path.
    private static final int LOAD_PARALLELISM = Math.max(1, Math.min(8,
        Integer.getInteger("muxi.loadParallelism", Runtime.getRuntime().availableProcessors()-2)));
    private static final Semaphore LOAD_SLOTS = new Semaphore(LOAD_PARALLELISM,true);
    public static int loadBacklog() {
        return SERVERS.values().stream().flatMap(r->r.worlds.values().stream())
            .mapToInt(s->s.loader.getQueue().size()).sum();
    }
    private final ConcurrentMap<ServerLevel, State> worlds = new ConcurrentHashMap<>();
    private final Queue<Runnable> afterBarrier = new ConcurrentLinkedQueue<>();
    private final Samples waves = new Samples();
    private volatile boolean ticking;

    public static boolean onTickWorker() { return TICK.get() != null; }
    public static boolean inParallelPhase(MinecraftServer server) {
        var runtime = SERVERS.get(server); return runtime != null && runtime.ticking;
    }
    public static void validate() {
        if (ENABLED && net.neoforged.fml.ModList.get().isLoaded("c2me"))
            throw new IllegalStateException("muxi.dimensionThreads requires removing C2ME from this test instance");
    }
    private static DimensionThreads runtime(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new DimensionThreads());
    }
    private State state(ServerLevel level) { return worlds.computeIfAbsent(level, State::new); }
    public static Executor loader(ServerLevel level, Executor fallback) {
        return ENABLED ? runtime(level.getServer()).state(level).loader : fallback;
    }
    public static ExecutorService generationExecutor(ExecutorService fallback) {
        State state = LOADING.get(); return state == null ? fallback : state.loader;
    }
    public static boolean defer(MinecraftServer server, Runnable operation) {
        if (!onTickWorker()) return false;
        runtime(server).afterBarrier.add(operation); return true;
    }
    public static void checkChunkOwner(ServerLevel requested) {
        var owner = TICK.get();
        if (owner != null && owner.level != requested)
            throw new IllegalStateException("Cross-world synchronous chunk access from " + owner.id + " to " + requested.dimension().location());
    }
    public static void checkMutation(ServerLevel level) {
        if (!ENABLED) return;
        if (Thread.currentThread() != ((LevelOwnerAccess)level).muxi$getOwner())
            throw new IllegalStateException("Off-owner world mutation in " + level.dimension().location() + " from " + Thread.currentThread().getName());
    }
    public static void generated(ServerLevel level, String status) {
        if (!ENABLED) return;
        State state = runtime(level.getServer()).state(level);
        state.steps.computeIfAbsent(status, ignored -> new LongAdder()).increment();
        state.generationThreads.add(Thread.currentThread().getName());
    }
    public static void tick(MinecraftServer server, ServerLevel[] levels, BooleanSupplier timeLeft) {
        DimensionThreads runtime = runtime(server);
        if (!server.isSameThread()) throw new IllegalStateException("World barrier must start on coordinator");
        for (ServerLevel level : levels) if (server.getTickCount() % 20 == 0)
            server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(),
                level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)), level.dimension());
        long start = System.nanoTime();
        List<CompletableFuture<Void>> work = new ArrayList<>();
        runtime.ticking = true;
        try {
            for (ServerLevel level : levels) {
                State state = runtime.state(level);
                work.add(CompletableFuture.runAsync(() -> state.tick(timeLeft), state.ticker));
            }
            // Join every worker even on failure. Never enter save/global phases with a live tick worker.
            // The native server watchdog and isolated test runner handle a truly hung worker.
            CompletableFuture.allOf(work.toArray(CompletableFuture[]::new)).join();
            var timing=((ServerTimingAccess)server).muxi$worldTimes();
            for(ServerLevel level:levels)timing.computeIfAbsent(level.dimension(),key->new long[100])[server.getTickCount()%100]=runtime.state(level).lastTickNanos;
        } catch (Exception failure) {
            throw new IllegalStateException("Dimension tick barrier failed; no tick errors are suppressed", failure);
        } finally { runtime.ticking = false; runtime.waves.add(System.nanoTime() - start); }
        // No world workers may mutate state while cross-world and global operations run.
        Runnable pending;
        while ((pending = runtime.afterBarrier.poll()) != null) pending.run();
    }
    public static Map<String, Object> metrics(MinecraftServer server) {
        DimensionThreads runtime = SERVERS.get(server);
        if (runtime == null) return Map.of("enabled", false);
        var worlds = new TreeMap<String, Object>();
        runtime.worlds.forEach((level, state) -> worlds.put(state.id, state.metrics()));
        return Map.of("enabled", true, "wave", runtime.waves.snapshot(), "worlds", worlds,
            "loadParallelism",LOAD_PARALLELISM,"loadBacklog",loadBacklog(),"travel",ChunkTravel.metrics(server));
    }
    public static void close(MinecraftServer server) {
        DimensionThreads runtime = SERVERS.remove(server);
        if (runtime == null) return;
        runtime.worlds.values().forEach(state -> { state.loader.shutdown(); state.ticker.shutdown(); });
        for (State state : runtime.worlds.values()) for (ExecutorService executor : List.of(state.loader, state.ticker)) {
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow(); throw new IllegalStateException("Dimension executor did not drain: " + state.id);
                }
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }
    }
    private static final class State {
        final ServerLevel level;
        final String id;
        final ThreadPoolExecutor loader;
        final ExecutorService ticker;
        final Samples ticks = new Samples();
        final LongAdder loadNanos = new LongAdder(), loadTasks = new LongAdder();
        final AtomicInteger maxQueue = new AtomicInteger();
        final ConcurrentMap<String, LongAdder> steps = new ConcurrentHashMap<>();
        final Set<String> generationThreads = ConcurrentHashMap.newKeySet();
        volatile String tickThread = "not-started";
        volatile long lastTickNanos;
        State(ServerLevel level) {
            this.level = level; id = level.dimension().location().toString();
            ticker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                // Cupboard's entity queue recognizes server owners by the literal word "server".
                // These are real phase owners; loader threads deliberately never use that name.
                runnable -> new Thread(runnable, "muxi-server-tick-" + id), new ThreadPoolExecutor.AbortPolicy());
            loader = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8192),
                runnable -> {var thread=new Thread(runnable, "muxi-load-" + id);thread.setPriority(Thread.NORM_PRIORITY-1);return thread;}, new ThreadPoolExecutor.AbortPolicy()) {
                private long started;
                @Override protected void beforeExecute(Thread thread, Runnable task) {
                    LOAD_SLOTS.acquireUninterruptibly();
                    LOADING.set(State.this); started = System.nanoTime(); maxQueue.accumulateAndGet(getQueue().size(), Math::max);
                }
                @Override protected void afterExecute(Runnable task, Throwable failure) {
                    loadNanos.add(System.nanoTime() - started); loadTasks.increment(); LOADING.remove();
                    LOAD_SLOTS.release();
                }
            };
        }
        void tick(BooleanSupplier timeLeft) {
            var owner = (LevelOwnerAccess) level;
            var cache = (ChunkOwnerAccess) level.getChunkSource();
            Thread oldLevel = owner.muxi$getOwner(), oldCache = cache.muxi$getOwner();
            long started = System.nanoTime();
            TICK.set(this); tickThread = Thread.currentThread().getName();
            owner.muxi$setOwner(Thread.currentThread()); cache.muxi$setOwner(Thread.currentThread());
            try {
                EventHooks.fireLevelTickPre(level, timeLeft);
                level.tick(timeLeft);
                EventHooks.fireLevelTickPost(level, timeLeft);
            } finally {
                owner.muxi$setOwner(oldLevel); cache.muxi$setOwner(oldCache);
                TICK.remove(); lastTickNanos=System.nanoTime()-started; ticks.add(lastTickNanos);
            }
        }
        Map<String, Object> metrics() {
            var counts = new TreeMap<String, Long>(); steps.forEach((name, count) -> counts.put(name, count.sum()));
            return Map.of("tickThread", tickThread, "tick", ticks.snapshot(), "loadTasks", loadTasks.sum(),
                "loadWorkMillis", loadNanos.sum() / 1e6, "maxLoadQueue", maxQueue.get(), "generationSteps", counts,
                "generationThreads", new TreeSet<>(generationThreads));
        }
    }
    private static final class Samples {
        private final long[] ring = new long[12000];
        private long count, total;
        synchronized void add(long value) { ring[(int)(count++ % ring.length)] = value; total += value; }
        synchronized Map<String, Object> snapshot() {
            int size = (int)Math.min(count, ring.length); long[] sorted = Arrays.copyOf(ring, size); Arrays.sort(sorted);
            return Map.of("count", count, "totalMillis", total / 1e6, "meanMillis", count == 0 ? 0 : total / 1e6 / count,
                "p95Millis", size == 0 ? 0 : sorted[(int)((size - 1) * .95)] / 1e6,
                "p99Millis", size == 0 ? 0 : sorted[(int)((size - 1) * .99)] / 1e6);
        }
    }
}
