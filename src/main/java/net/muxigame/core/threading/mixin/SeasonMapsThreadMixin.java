package net.muxigame.core.threading.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.HashMap;
import java.util.function.Function;

/** Protect Serene Seasons' shared map internals; each world's values retain their own tick owner. */
@Pseudo
@Mixin(targets="sereneseasons.season.SeasonHandler",remap=false)
public abstract class SeasonMapsThreadMixin {
    @WrapOperation(method="onLevelTick",at=@At(value="INVOKE",target="Ljava/util/HashMap;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object muxi$get(HashMap<Object,Object> map,Object key,Object fallback,Operation<Object> original) {
        synchronized(map) { return original.call(map,key,fallback); }
    }
    @WrapOperation(method={"onLevelTick","sendSeasonUpdate"},at=@At(value="INVOKE",target="Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object muxi$put(HashMap<Object,Object> map,Object key,Object value,Operation<Object> original) {
        synchronized(map) { return original.call(map,key,value); }
    }
    @WrapOperation(method="sendSeasonUpdate",at=@At(value="INVOKE",target="Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object muxi$compute(HashMap<Object,Object> map,Object key,Function<Object,Object> factory,Operation<Object> original) {
        synchronized(map) { return original.call(map,key,factory); }
    }
}
