package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.*;
@Mixin(Mob.class)
public abstract class FreshMobSpawnMixin implements FreshMobSpawn {
 @Unique private MobSpawnType muxi$freshReason;
 @Inject(method="finalizeSpawn",at=@At("HEAD")) private void muxi$cause(ServerLevelAccessor level,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData group,CallbackInfoReturnable<SpawnGroupData> ci){muxi$freshReason=reason;}
 public MobSpawnType muxi$freshSpawnReason(){return muxi$freshReason;}
 public void muxi$clearFreshSpawnReason(){muxi$freshReason=null;}
}
