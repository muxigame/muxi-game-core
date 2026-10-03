import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;import net.minecraft.server.packs.resources.ReloadInstance;
import net.muxigame.shadernative.JoinObservation;import net.muxigame.shadernative.mixin.JoinReload;
public final class ReloadObservationTest {
 private static int checks;private static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  var wrapper=new JoinReload(){};var method=JoinReload.class.getDeclaredMethod("qa$reload",Executor.class,Executor.class,CompletableFuture.class,List.class,Operation.class);method.setAccessible(true);
  var future=new CompletableFuture<Void>();ReloadInstance nativeResult=new ReloadInstance(){public CompletableFuture<?> done(){return future;}public float getActualProgress(){return 0;}};AtomicInteger calls=new AtomicInteger();Operation<ReloadInstance> original=(objects)->{calls.incrementAndGet();return nativeResult;};Executor direct=Runnable::run;
  JoinObservation.begin("join-0");Object result=method.invoke(wrapper,direct,direct,CompletableFuture.completedFuture(null),List.of(),original);check(result==nativeResult);check(calls.get()==1);check(nativeResult.done()==future);check(!future.isDone());future.complete(null);check(future.join()==null);check(JoinObservation.snapshot().getAsJsonArray("events").size()==3);JoinObservation.finish();
  var failure=new IllegalStateException("native");Operation<ReloadInstance> throwing=(objects)->{calls.incrementAndGet();throw failure;};JoinObservation.begin("join-1");try{method.invoke(wrapper,direct,direct,CompletableFuture.completedFuture(null),List.of(),throwing);throw new AssertionError();}catch(InvocationTargetException wrapped){check(wrapped.getCause()==failure);}check(calls.get()==2);JoinObservation.finish();
  result=method.invoke(wrapper,direct,direct,CompletableFuture.completedFuture(null),List.of(),original);check(result==nativeResult);check(calls.get()==3);System.out.println("PASS "+checks+" native-return/future/exception identity assertions");
 }
}
