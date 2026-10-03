import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.muxigame.core.feature.dimensions.initialspawn.InitialSpawnSearch;
import net.muxigame.core.feature.dimensions.initialspawn.InitialSpawnSearch.*;

public final class InitialSpawnSearchTest {
    static void check(boolean result, String why) { if (!result) throw new AssertionError(why); }
    static final class World implements Chunks {
        final List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        final List<Column> inspected = new ArrayList<>();
        int releases, scheduled;
        boolean loaded = true, autoComplete, unsafe;
        @Override public boolean inBounds(int x, int z) { return true; }
        @Override public CompletableFuture<Boolean> requestFull(int x, int z) {
            scheduled++;
            var future = new CompletableFuture<Boolean>();
            if (autoComplete) future.complete(true);
            futures.add(future);
            return future;
        }
        @Override public boolean readyNow(int x, int z) { return loaded; }
        @Override public Integer safeHeight(int x, int z) {
            check(loaded && futures.stream().allMatch(f -> f.isDone() && Boolean.TRUE.equals(f.getNow(false))), "world reads before FULL");
            inspected.add(new Column(x,z));
            return unsafe ? null : 80;
        }
        @Override public void release() { releases++; }
    }
    public static void main(String[] args) {
        // Geometry includes all nine +/-4 columns and their hidden +/-2 biome lookups, including negative chunks.
        for (int x=-33;x<=33;x++) for (int z=-33;z<=33;z++) {
            var footprint = InitialSpawnSearch.footprint(x,z);
            check(footprint.size()<=4,"bounded footprint");
            for (int dx=-6;dx<=6;dx++) for (int dz=-6;dz<=6;dz++)
                check(footprint.contains(new Column((x+dx)>>4,(z+dz)>>4)),"missing halo chunk");
        }
        World world = new World();
        InitialSpawnSearch search = new InitialSpawnSearch(() -> 0.0,world);
        check(search.step(1,2,System.nanoTime()-1).scheduled()==0,"expired cooperative budget schedules work");
        for (int n=0;n<4;n++) {
            var progress=search.step(1,2);
            check(progress.scheduled()==1 && progress.inspected()==0,"per-step scheduling budget / pending safety");
            check(progress.status()==Status.WAITING,"unfinished future must not block or become ready");
        }
        check(world.inspected.isEmpty(),"pending generation read world");
        world.futures.forEach(f -> f.complete(true));
        world.loaded=false;
        check(search.step(0,2).status()==Status.WAITING && world.inspected.isEmpty(),"future alone is not getChunkNow readiness");
        world.loaded=true;
        check(search.step(0,0).inspected()==0,"zero global check budget");
        check(search.step(0,1).status()==Status.READY,"ready search completes");
        check(search.landing().equals(new Landing(0,80,0)),"first safe offset order");
        check(world.releases==0,"ready handoff must retain tickets");
        world.loaded=false;
        check(!search.readyNow(),"handoff must reject lost readiness");
        search.close();search.close();
        check(world.releases==1 && search.landing()==null,"close releases exactly once");
        check(world.futures.stream().noneMatch(CompletableFuture::isCancelled),"shared generation cancelled");

        World other=new World();InitialSpawnSearch live=new InitialSpawnSearch(() -> 0.0,other);
        live.step(1,1);
        World disconnected=new World();InitialSpawnSearch gone=new InitialSpawnSearch(() -> 0.0,disconnected);
        gone.step(1,1);gone.close();
        check(other.releases==0 && !other.futures.getFirst().isCancelled(),"one request cleanup affects another");
        other.futures.getFirst().completeExceptionally(new IllegalStateException("generation failure"));
        for(int i=0;i<4;i++) live.step(1,1);
        check(live.step(0,1).status()==Status.FAILED,"exceptional generation falls through");
        live.close();

        World unsafe=new World();unsafe.autoComplete=true;unsafe.unsafe=true;
        InitialSpawnSearch exhausted=new InitialSpawnSearch(() -> 0.0,unsafe);
        Status status=Status.WAITING;
        for(int tick=0;tick<1000 && status==Status.WAITING;tick++) {
            var progress=exhausted.step(1,2);
            check(progress.scheduled()<=1 && progress.inspected()<=2,"bounded tick work");
            status=progress.status();
        }
        check(status==Status.EXHAUSTED && exhausted.attempts()==24,"attempt bound");
        check(unsafe.inspected.size()==24*9 && unsafe.releases==24,"all original offsets / candidate cleanup");
        exhausted.close();
        System.out.println("PASS search: +/-6 footprint, budgets, unresolved futures, FULL+loaded readiness, held handoff, cleanup, independent requests, failed generation, 24x9 bound");
    }
}
