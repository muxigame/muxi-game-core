package net.muxigame.core.compat.mixin.goblintraders;

import com.mrcrayfish.goblintraders.spawner.*;
import com.mrcrayfish.goblintraders.entity.AbstractGoblinEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;

/** Native trader timers, saved independently in every overworld-like exploration world. */
@Mixin(value=GoblinTraderSpawner.class,remap=false)
public abstract class SurvivalGoblinSpawnerMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private EntityType<? extends AbstractGoblinEntity> type;
    @Shadow @Final private IGoblinData data;
    @Unique private Map<ResourceKey<Level>,GoblinTraderSpawner> muxi$explorationSpawners;
    @Inject(method="serverTick",at=@At("HEAD"))
    private void muxi$tickSurvivalTrader(CallbackInfo callback) {
        if(!level.dimension().equals(Level.OVERWORLD))return;
        if(muxi$explorationSpawners==null)muxi$explorationSpawners=new HashMap<>();
        for(var dimension:WorldDimensions.EXPLORATION) {
            ServerLevel survival=level.getServer().getLevel(dimension);
            if(survival==null)continue;
            GoblinTraderSpawner spawner=muxi$explorationSpawners.get(dimension);
            if(spawner==null) {
                var factory=new SavedData.Factory<GoblinTraderSpawner>(
                    ()->new GoblinTraderSpawner(survival,type,data),
                    (tag,registries)->new GoblinTraderSpawner(survival,type,data).load(tag),null);
                spawner=survival.getDataStorage().computeIfAbsent(factory,"muxi_goblin_trader");
                muxi$explorationSpawners.put(dimension,spawner);
            }
            spawner.serverTick();
        }
    }
}
