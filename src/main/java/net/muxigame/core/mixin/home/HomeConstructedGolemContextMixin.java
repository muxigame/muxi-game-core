package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
@Mixin(net.minecraft.world.level.block.CarvedPumpkinBlock.class)
public abstract class HomeConstructedGolemContextMixin {
 @WrapMethod(method="trySpawnGolem") private void muxi$construct(Level level,BlockPos pos,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(level,pos);}}
}
