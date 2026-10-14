package net.muxigame.core.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.dimensions.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Preserve the old home's default spawn separately from the new primary world's spawn. */
@Mixin(Level.class)
public abstract class HomeWorldSpawnMixin {
 @Inject(method="getSharedSpawnPos",at=@At("HEAD"),cancellable=true)private void muxi$homeSpawn(CallbackInfoReturnable<BlockPos> ci){if((Object)this instanceof ServerLevel level&&WorldDimensions.home(level.dimension())){BlockPos spawn=WorldRoleState.get(level.getServer()).homeSpawn();if(spawn!=null)ci.setReturnValue(spawn);}}
 @Inject(method="getSharedSpawnAngle",at=@At("HEAD"),cancellable=true)private void muxi$homeAngle(CallbackInfoReturnable<Float> ci){if((Object)this instanceof ServerLevel level&&WorldDimensions.home(level.dimension())){var state=WorldRoleState.get(level.getServer());if(state.homeSpawn()!=null)ci.setReturnValue(state.homeAngle());}}
}
