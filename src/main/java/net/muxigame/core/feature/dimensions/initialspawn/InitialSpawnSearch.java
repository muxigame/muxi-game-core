package net.muxigame.core.feature.dimensions.initialspawn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;

/** Main-thread, incremental search. Future completion never accesses the world. */
public final class InitialSpawnSearch implements AutoCloseable {
    public static final int RADIUS = 10_000;
    public static final int MAX_ATTEMPTS = 24;
    private static final int[][] OFFSETS = {{0,0},{4,0},{-4,0},{0,4},{0,-4},{4,4},{4,-4},{-4,4},{-4,-4}};
    public record Column(int x, int z) {}
    public record Landing(int x, int y, int z) {}
    public enum Status { WAITING, READY, EXHAUSTED, FAILED }
    public record Progress(Status status, int scheduled, int inspected) {
        Progress(Status status, int scheduled) { this(status, scheduled, 0); }
    }
    public interface Chunks {
        boolean inBounds(int x, int z);
        CompletableFuture<Boolean> requestFull(int chunkX, int chunkZ);
        boolean readyNow(int chunkX, int chunkZ);
        /** Called only after every chunk in the candidate footprint is ready. */
        Integer safeHeight(int x, int z);
        void release();
    }
    private final DoubleSupplier random;
    private final Chunks chunks;
    private List<Column> footprint = List.of(), columns = List.of();
    private final List<CompletableFuture<Boolean>> futures = new ArrayList<>();
    private int attempts, checked;
    private Landing landing;
    private boolean closed, failed;

    public InitialSpawnSearch(DoubleSupplier random, Chunks chunks) {
        this.random = random;
        this.chunks = chunks;
    }

    /** Offsets extend by 4, collision by 1 and biome lookup by 2: prepare center +/-6. */
    public static List<Column> footprint(int x, int z) {
        var result = new ArrayList<Column>(4);
        for (int cx = (x - 6) >> 4; cx <= (x + 6) >> 4; cx++)
            for (int cz = (z - 6) >> 4; cz <= (z + 6) >> 4; cz++) result.add(new Column(cx, cz));
        return List.copyOf(result);
    }

    public Progress step(int schedulingBudget, int checkBudget) {
        return step(schedulingBudget, checkBudget, 0);
    }

    /** Cooperative deadline: an individual vendor call cannot be preempted. */
    public Progress step(int schedulingBudget, int checkBudget, long deadlineNs) {
        if (closed || failed) return new Progress(Status.FAILED, 0);
        if (landing != null) return new Progress(Status.READY, 0);
        int scheduled = 0, inspected = 0;
        try {
            if (footprint.isEmpty()) {
                if (attempts >= MAX_ATTEMPTS) return new Progress(Status.EXHAUSTED, 0);
                attempts++;
                double radius = Math.sqrt(random.getAsDouble()) * RADIUS;
                double angle = random.getAsDouble() * Math.PI * 2.0;
                int x = (int)Math.floor(Math.cos(angle) * radius);
                int z = (int)Math.floor(Math.sin(angle) * radius);
                var possible = new ArrayList<Column>(9);
                for (int[] offset : OFFSETS) {
                    int px = x + offset[0], pz = z + offset[1];
                    if ((long)px * px + (long)pz * pz <= (long)RADIUS * RADIUS && chunks.inBounds(px, pz))
                        possible.add(new Column(px, pz));
                }
                if (possible.isEmpty()) return new Progress(Status.WAITING, 0);
                columns = possible;
                footprint = footprint(x, z);
            }
            while (futures.size() < footprint.size() && scheduled < schedulingBudget) {
                if (deadlineNs != 0 && System.nanoTime() - deadlineNs >= 0) break;
                Column chunk = footprint.get(futures.size());
                scheduled++;
                futures.add(chunks.requestFull(chunk.x(), chunk.z()));
            }
            if (futures.size() != footprint.size()) return new Progress(Status.WAITING, scheduled);
            for (var future : futures) {
                if (!future.isDone()) return new Progress(Status.WAITING, scheduled);
                if (!Boolean.TRUE.equals(future.getNow(false))) {
                    failed = true;
                    return new Progress(Status.FAILED, scheduled);
                }
            }
            if (!readyNow()) return new Progress(Status.WAITING, scheduled);
            for (int n = 0; n < checkBudget && checked < columns.size(); n++) {
                if (deadlineNs != 0 && System.nanoTime() - deadlineNs >= 0) break;
                Column column = columns.get(checked++);
                inspected++;
                Integer height = chunks.safeHeight(column.x(), column.z());
                if (height != null) {
                    landing = new Landing(column.x(), height, column.z());
                    return new Progress(Status.READY, scheduled, inspected);
                }
            }
            if (checked == columns.size()) clearCandidate();
            return new Progress(Status.WAITING, scheduled, inspected);
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            return new Progress(Status.FAILED, scheduled, inspected);
        }
    }

    public boolean readyNow() {
        if (closed || failed || footprint.isEmpty() || futures.size() != footprint.size()) return false;
        for (var future : futures)
            if (!future.isDone() || future.isCompletedExceptionally() || !Boolean.TRUE.equals(future.getNow(false))) return false;
        for (Column chunk : footprint) if (!chunks.readyNow(chunk.x(), chunk.z())) return false;
        return true;
    }
    public Landing landing() { return landing; }
    public int attempts() { return attempts; }
    private void clearCandidate() {
        chunks.release();
        footprint = List.of();
        columns = List.of();
        // These are views of native shared work: never cancel the underlying generation futures.
        futures.clear();
        checked = 0;
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        landing = null;
        clearCandidate();
    }
}
