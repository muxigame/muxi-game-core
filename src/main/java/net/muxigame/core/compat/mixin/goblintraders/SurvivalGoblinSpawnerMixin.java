package net.muxigame.core.compat.mixin.goblintraders;

import com.mrcrayfish.goblintraders.spawner.*;
import com.mrcrayfish.goblintraders.entity.AbstractGoblinEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A second native trader timer, saved in survival and ticked once alongside the home timer. */
@Mixin(value=GoblinTraderSpawner.class,remap=false)
public abstract class SurvivalGoblinSpawnerMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private EntityType<? extends AbstractGoblinEntity> type;
    @Shadow @Final private IGoblinData data;
    @Unique private GoblinTraderSpawner muxi$survivalSpawner;
    @Inject(method="serverTick",at=@At("HEAD"))
    private void muxi$tickSurvivalTrader(CallbackInfo callback) {
        if(!level.dimension().equals(Level.OVERWORLD))return;
        ServerLevel survival=level.getServer().getLevel(WorldDimensions.OVERWORLD);
        if(survival==null)return;
        if(muxi$survivalSpawner==null) {
            var factory=new SavedData.Factory<GoblinTraderSpawner>(
                ()->new GoblinTraderSpawner(survival,type,data),
                (tag,registries)->new GoblinTraderSpawner(survival,type,data).load(tag),null);
            muxi$survivalSpawner=survival.getDataStorage().computeIfAbsent(factory,"muxi_goblin_trader");
        }
        muxi$survivalSpawner.serverTick();
    }
}
