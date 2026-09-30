package net.muxigame.core.compat.mixin.blueprint;

import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import java.util.*;

/** Apply the same ordered overworld generator modifiers, preserving explicit survival additions. */
@Mixin(targets="com.teamabnormals.blueprint.common.world.modification.chunk.ChunkGeneratorModificationManager",remap=false)
public abstract class SurvivalSurfaceRulesMixin {
    @Redirect(method="lambda$static$4",at=@At(value="INVOKE",target="Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object muxi$inheritSurfaceRules(HashMap<?,?> modifiers,Object key) {
        Object own=modifiers.get(key);
        if(!WorldDimensions.OVERWORLD.location().equals(key))return own;
        Object home=modifiers.get(Level.OVERWORLD.location());
        if(!(home instanceof List<?> list))return own;
        var merged=new LinkedList<Object>(list);
        if(own instanceof List<?> additions)for(Object modifier:additions)if(!merged.contains(modifier))merged.add(modifier);
        return merged;
    }
}
