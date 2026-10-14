package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
@Pseudo @Mixin(targets="com.github.alexthe666.alexsmobs.block.BlockTriopsEggs")
public abstract class HomeTriopsEggContextMixin {
 @WrapMethod(method="tick") private void muxi$hatch(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(state,level,pos,random);}}
}
