import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Executable ownership model only. Does NOT move Minecraft ticks or C2ME work. */
public final class DimensionThreadModel implements AutoCloseable {
    public record PreparedChunk(long coordinate, String immutableData) {}
    public record Player(UUID id, int items, int experience) {}

    /** Bounded mailbox; rejected work never runs on the submitting thread. */
    public static final class Actor implements AutoCloseable {
        private final BlockingQueue<Runnable> queue;
        private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
        private final Thread thread;
        private final Runnable tick;
        private final long period;
        private volatile boolean accepting = true;
        private volatile Throwable failure;

        Actor(String name, int capacity, Duration period, Runnable tick) {
            queue = new ArrayBlockingQueue<>(capacity);
            this.period = period == null ? 0 : period.toNanos();
            this.tick = tick;
            thread = new Thread(this::loop, name);
            thread.start();
        }

        public boolean ownsThread() { return Thread.currentThread() == thread; }
        public void requireOwner() {
            if (!ownsThread()) throw new IllegalStateException("Wrong owner: " + thread.getName());
        }

        public synchronized <T> CompletableFuture<T> submit(Callable<T> work) {
            var result = new CompletableFuture<T>();
            if (!accepting) return CompletableFuture.failedFuture(
                new RejectedExecutionException("Stopped: " + thread.getName(), failure));
            pending.add(result);
            Runnable task = () -> {
                if (result.isDone()) { pending.remove(result); return; }
                try { result.complete(work.call()); }
                catch (Throwable error) { result.completeExceptionally(error); }
                finally { pending.remove(result); }
            };
            if (!queue.offer(task)) {
                pending.remove(result);
                result.completeExceptionally(new RejectedExecutionException("Mailbox full: " + thread.getName()));
            }
            return result;
        }

        private void loop() {
            long next = System.nanoTime();
            try {
                while (accepting) {
                    if (tick == null) queue.take().run();
                    else {
                        long now = System.nanoTime();
                        if (now >= next) {
                            tick.run();
                            // Do not create an unbounded catch-up burst after an overloaded tick.
                            next = Math.max(next + period, System.nanoTime());
                        }
                        Runnable task = queue.poll(Math.max(0, next - System.nanoTime()), TimeUnit.NANOSECONDS);
                        if (task != null) task.run();
                    }
                }
            } catch (InterruptedException error) {
                if (accepting) failure = error;
            } catch (Throwable error) { failure = error; }
            finally { stop(failure == null ? new CancellationException("Actor stopped") : failure); }
        }

        private synchronized void stop(Throwable cause) {
            accepting = false;
            queue.clear();
            for (var future : pending) future.completeExceptionally(cause);
            pending.clear();
        }

        @Override public void close() {
            if (ownsThread()) throw new IllegalStateException("Close from external coordinator only");
            stop(new CancellationException("Actor closed"));
            thread.interrupt();
            try { thread.join(3000); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            if (thread.isAlive()) throw new IllegalStateException("Uncooperative task: " + thread.getName());
        }
    }

    public static final class World {
        public final Actor tickThread;
        public final Actor loadThread;
        private final Map<Long, PreparedChunk> chunks = new HashMap<>();
        private final Map<UUID, Player> players = new HashMap<>();

        World(String id, Runnable onTick, int capacity) {
            tickThread = new Actor("model-" + id + "-tick", capacity, Duration.ofMillis(10), onTick);
            loadThread = new Actor("model-" + id + "-load", capacity, null, null);
        }

        /** Loader prepares detached values. Only the owner may publish into live world state. */
        public CompletableFuture<PreparedChunk> load(Callable<PreparedChunk> prepare) {
            return loadThread.submit(prepare).thenCompose(chunk -> tickThread.submit(() -> {
                tickThread.requireOwner();
                chunks.put(chunk.coordinate(), chunk);
                return chunk;
            }));
        }

        public CompletableFuture<PreparedChunk> chunk(long coordinate) {
            return tickThread.submit(() -> chunks.get(coordinate));
        }
        public CompletableFuture<Player> player(UUID id) {
            return tickThread.submit(() -> players.get(id));
        }
        public CompletableFuture<Void> add(Player player) {
            return tickThread.submit(() -> { attach(player); return null; });
        }
        private void attach(Player player) {
            tickThread.requireOwner();
            if (players.putIfAbsent(player.id(), player) != null)
                throw new IllegalStateException("Duplicate player");
        }
        private Player detach(UUID id) {
            tickThread.requireOwner();
            var player = players.remove(id);
            if (player == null) throw new IllegalStateException("Missing source player");
            return player;
        }
    }

    public final Actor coordinator = new Actor("model-coordinator", 256, null, null);
    public final World home;
    public final World survival;
    private final Set<UUID> transferring = new HashSet<>(); // Coordinator-owned.
    private final Set<CompletableFuture<?>> operations = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public DimensionThreadModel(Runnable homeTick, Runnable survivalTick, int capacity) {
        home = new World("home", homeTick, capacity);
        survival = new World("survival", survivalTick, capacity);
    }

    /** In-memory handoff with rollback. A real adapter also needs durable transfer intents. */
    public CompletableFuture<Void> transfer(UUID id, World source, World target, Supplier<Boolean> targetAccepts) {
        if (closed) return CompletableFuture.failedFuture(new RejectedExecutionException("Model closed"));
        var result = new CompletableFuture<Void>();
        operations.add(result);
        result.whenComplete((value, error) -> operations.remove(result));
        coordinator.submit(() -> {
            coordinator.requireOwner();
            if (source == target || !transferring.add(id)) {
                result.completeExceptionally(new IllegalStateException("Invalid or duplicate transfer"));
                return null;
            }
            source.tickThread.submit(() -> source.detach(id)).whenComplete((player, detachError) -> {
                if (detachError != null) { finish(id, result, detachError); return; }
                target.tickThread.submit(() -> {
                    if (!targetAccepts.get()) throw new IllegalStateException("Destination rejected handoff");
                    target.attach(player);
                    return null;
                }).whenComplete((ignored, attachError) -> {
                    if (attachError == null) finish(id, result, null);
                    else source.tickThread.submit(() -> { source.attach(player); return null; })
                        .whenComplete((rolledBack, rollbackError) -> {
                            if (rollbackError != null) attachError.addSuppressed(rollbackError);
                            finish(id, result, attachError);
                        });
                });
            });
            return null;
        }).whenComplete((ignored, error) -> { if (error != null) result.completeExceptionally(error); });
        return result;
    }

    private void finish(UUID id, CompletableFuture<Void> result, Throwable error) {
        coordinator.submit(() -> {
            transferring.remove(id);
            if (error == null) result.complete(null); else result.completeExceptionally(error);
            return null;
        }).whenComplete((ignored, dispatchError) -> { if (dispatchError != null) result.completeExceptionally(dispatchError); });
    }

    @Override public void close() {
        closed = true;
        // This model cancels on shutdown. Minecraft integration must drain and persist instead.
        var failures = new ArrayList<RuntimeException>();
        for (var actor : List.of(home.loadThread, survival.loadThread, home.tickThread, survival.tickThread, coordinator)) {
            try { actor.close(); } catch (RuntimeException error) { failures.add(error); }
        }
        for (var future : operations) future.completeExceptionally(new CancellationException("Model closed"));
        if (!failures.isEmpty()) throw failures.getFirst();
    }
}
