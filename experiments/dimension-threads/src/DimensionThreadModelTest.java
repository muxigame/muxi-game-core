import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class DimensionThreadModelTest {
    private static final List<String> passed = new ArrayList<>();
    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
    private static void signal(CountDownLatch latch) throws Exception {
        if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Signal timed out");
    }
    private static void check(String description, boolean condition) {
        if (!condition) throw new AssertionError(description);
        passed.add(description);
    }
    private static boolean fails(CompletableFuture<?> future) throws Exception {
        try { await(future); return false; }
        catch (ExecutionException | CancellationException expected) { return true; }
    }

    private static void isolationAndTransfers() throws Exception {
        var homeTicks = new CountDownLatch(10);
        var survivalTicks = new CountDownLatch(10);
        var releaseLoad = new CountDownLatch(1);
        var loadStarted = new CountDownLatch(1);
        try (var model = new DimensionThreadModel(homeTicks::countDown, survivalTicks::countDown, 16)) {
            var names = new HashSet<String>();
            for (var actor : List.of(model.coordinator, model.home.tickThread, model.home.loadThread,
                    model.survival.tickThread, model.survival.loadThread)) {
                names.add(await(actor.submit(() -> { actor.requireOwner(); return Thread.currentThread().getName(); })));
            }
            check("five distinct owners: coordinator plus two tick and two loader threads", names.size() == 5);
            boolean rejected = false;
            try { model.home.tickThread.requireOwner(); } catch (IllegalStateException expected) { rejected = true; }
            check("off-owner live state access rejected", rejected);

            var blocked = model.home.load(() -> {
                model.home.loadThread.requireOwner();
                loadStarted.countDown(); signal(releaseLoad);
                return new DimensionThreadModel.PreparedChunk(1, "home-generated-data");
            });
            signal(loadStarted);
            signal(homeTicks); signal(survivalTicks);
            check("both ticks progress while home loader is blocked", !blocked.isDone());
            var remote = await(model.survival.load(() -> new DimensionThreadModel.PreparedChunk(2, "survival-data")));
            check("survival loading independent of blocked home loader", remote.coordinate() == 2);
            check("unprepared chunk not exposed to live tick state", await(model.home.chunk(1)) == null);
            releaseLoad.countDown(); await(blocked);
            check("prepared result published through owning tick mailbox", await(model.home.chunk(1)).immutableData().equals("home-generated-data"));
            check("loader exception reaches caller", fails(model.home.load(() -> { throw new IllegalArgumentException("fixture failure"); })));
            await(model.home.load(() -> new DimensionThreadModel.PreparedChunk(3, "recovered")));
            check("loader remains usable after request failure", await(model.home.chunk(3)) != null);

            var player = new DimensionThreadModel.Player(UUID.randomUUID(), 64, 37);
            await(model.home.add(player));
            await(model.transfer(player.id(), model.home, model.survival, () -> {
                model.survival.tickThread.requireOwner(); return true;
            }));
            check("handoff removes source and preserves destination inventory and XP",
                await(model.home.player(player.id())) == null && player.equals(await(model.survival.player(player.id()))));
            check("failed destination handoff reported", fails(model.transfer(player.id(), model.survival, model.home, () -> false)));
            check("failed destination restores source without duplicate",
                await(model.home.player(player.id())) == null && player.equals(await(model.survival.player(player.id()))));

            var targetEntered = new CountDownLatch(1);
            var releaseTarget = new CountDownLatch(1);
            var first = model.transfer(player.id(), model.survival, model.home, () -> {
                targetEntered.countDown();
                try { signal(releaseTarget); } catch (Exception e) { throw new RuntimeException(e); }
                return true;
            });
            try {
                signal(targetEntered);
                check("duplicate in-flight handoff rejected", fails(model.transfer(player.id(), model.survival, model.home, () -> true)));
            } finally { releaseTarget.countDown(); }
            await(first);

            for (int i = 0; i < 100; i++) {
                var source = i % 2 == 0 ? model.home : model.survival;
                var target = i % 2 == 0 ? model.survival : model.home;
                await(model.transfer(player.id(), source, target, () -> true));
                if (await(source.player(player.id())) != null || !player.equals(await(target.player(player.id()))))
                    throw new AssertionError("Transfer stress lost/duplicated state at " + i);
            }
            check("100 handoffs preserve exactly one owner and immutable player snapshot", true);
        } finally { releaseLoad.countDown(); }
    }

    private static void overloadAndShutdown() throws Exception {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var model = new DimensionThreadModel(() -> {}, () -> {}, 2);
        try {
            var first = model.home.load(() -> {
                running.countDown(); signal(release);
                return new DimensionThreadModel.PreparedChunk(1, "slow");
            });
            signal(running);
            var second = model.home.load(() -> new DimensionThreadModel.PreparedChunk(2, "queued"));
            var third = model.home.load(() -> new DimensionThreadModel.PreparedChunk(3, "queued"));
            var ranOnCaller = new AtomicInteger();
            var overflow = model.home.load(() -> { ranOnCaller.incrementAndGet(); return null; });
            check("bounded loader queue rejects overload without running on caller", fails(overflow) && ranOnCaller.get() == 0);
            model.close();
            check("shutdown resolves active and queued load futures", fails(first) && fails(second) && fails(third));
            check("requests after shutdown rejected", fails(model.home.load(() -> null)));
        } finally { release.countDown(); model.close(); }
    }

    private static void fatalTick() throws Exception {
        var failTick = new CountDownLatch(1);
        var tickReached = new CountDownLatch(1);
        var otherTicks = new CountDownLatch(10);
        try (var model = new DimensionThreadModel(() -> {
            if (failTick.getCount() == 0) { tickReached.countDown(); throw new IllegalStateException("fatal fixture tick"); }
        }, otherTicks::countDown, 4)) {
            failTick.countDown(); signal(tickReached); signal(otherTicks);
            check("fatal tick stops its actor and rejects later work", fails(model.home.tickThread.submit(() -> 1)));
            check("other dimension owner still responsive after peer failure", await(model.survival.tickThread.submit(() -> 7)) == 7);
        }
    }

    public static void main(String[] args) throws Exception {
        isolationAndTransfers(); overloadAndShutdown(); fatalTick();
        check("all model threads stopped", Thread.getAllStackTraces().keySet().stream()
            .noneMatch(t -> t.isAlive() && t.getName().startsWith("model-")));
        String items = passed.stream().map(s -> "    \"" + s + "\"").collect(java.util.stream.Collectors.joining(",\n"));
        Files.writeString(Path.of(args[0]), "{\n  \"success\": true,\n  \"minecraftIntegrated\": false,\n  \"passed\": [\n" + items + "\n  ]\n}\n");
        System.out.println("Dimension scheduling model: " + passed.size() + " checks passed; Minecraft integration is NOT enabled.");
    }
}
