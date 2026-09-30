package net.muxigame.core.taskssmoke;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/** Exercise the transformed Sable predicates during concurrent cache growth, before measurement. */
final class SableCacheRegression {
    @SuppressWarnings("unchecked")
    static void verify() throws Exception {
        if(!Boolean.getBoolean("muxi.dimensionThreads"))return;
        try {Class.forName("dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState");}
        catch(ClassNotFoundException absent){return;}
        var states=new ArrayList<BlockState>();
        for(var block:List.of(Blocks.AIR,Blocks.STONE,Blocks.WATER,Blocks.OAK_STAIRS,
                Blocks.COBBLESTONE_WALL,Blocks.OAK_FENCE,Blocks.SNOW,Blocks.OAK_SLAB))
            states.addAll(block.getStateDefinition().getPossibleStates());
        for(int predicate=1;predicate<=2;predicate++) {
            var type=Class.forName("dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState$"+predicate);
            var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
            var baseline=(BiFunction<BlockGetter,BlockState,Boolean>)constructor.newInstance();
            var expected=new IdentityHashMap<BlockState,Boolean>();
            for(var state:states)expected.put(state,baseline.apply(EmptyBlockGetter.INSTANCE,state));
            var shared=(BiFunction<BlockGetter,BlockState,Boolean>)constructor.newInstance();
            var cacheField=type.getDeclaredField("cache");cacheField.setAccessible(true);
            if(!(cacheField.get(shared) instanceof it.unimi.dsi.fastutil.objects.Reference2BooleanMaps.SynchronizedMap))
                throw new AssertionError("Sable cache compatibility mixin was not applied to "+type.getName());
            var workers=Executors.newFixedThreadPool(4);var gate=new CountDownLatch(1);
            try {
                var futures=new ArrayList<Future<?>>();
                for(int worker=0;worker<4;worker++) {
                    final int offset=worker*71;
                    futures.add(workers.submit(()->{
                        try {gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
                        for(int round=0;round<8;round++)for(int i=0;i<states.size();i++) {
                            var state=states.get((i+offset)%states.size());
                            if(!Objects.equals(expected.get(state),shared.apply(EmptyBlockGetter.INSTANCE,state)))
                                throw new AssertionError("Concurrent Sable collision cache changed result: "+state);
                        }
                    }));
                }
                gate.countDown();
                for(var future:futures)future.get(30,TimeUnit.SECONDS);
            }finally {workers.shutdownNow();}
        }
        System.out.println("SABLE_CACHE_CONCURRENT_REGRESSION passed: 2 predicates, "+states.size()+" states, 4 workers");
    }
}
