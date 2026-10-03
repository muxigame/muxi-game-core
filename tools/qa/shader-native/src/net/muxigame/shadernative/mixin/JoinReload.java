package net.muxigame.shadernative.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;import java.util.concurrent.*;
import net.minecraft.server.packs.PackResources;import net.minecraft.server.packs.resources.*;import net.minecraft.util.Unit;
import net.muxigame.shadernative.JoinObservation;import org.spongepowered.asm.mixin.Mixin;
@Mixin(ReloadableResourceManager.class)
public abstract class JoinReload {
 @WrapMethod(method="createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;")
 private ReloadInstance qa$reload(Executor prepare,Executor apply,CompletableFuture<Unit> initial,List<PackResources> packs,Operation<ReloadInstance> original){
  long token=JoinObservation.token();if(token==0)return original.call(prepare,apply,initial,packs);
  long id=JoinObservation.reloadBegin(token);ReloadInstance result;
  try{result=original.call(prepare,apply,initial,packs);}catch(RuntimeException|Error failure){JoinObservation.reloadEnd(token,id,failure);throw failure;}
  // Observe the native future; return the identical ReloadInstance and never replace/cancel its future.
  try{result.done().whenComplete((value,failure)->JoinObservation.reloadEnd(token,id,failure));}catch(RuntimeException observationFailure){JoinObservation.event(token,"reloadObserverFailure",observationFailure.getClass().getName());}
  return result;
 }
}
