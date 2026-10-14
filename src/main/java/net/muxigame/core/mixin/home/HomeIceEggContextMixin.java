package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import java.util.UUID;
@Pseudo @Mixin(targets="com.iafenvoy.iceandfire.item.block.entity.EggInIceBlockEntity")
public abstract class HomeIceEggContextMixin implements PlayerOwnedHatch {
 @Shadow public UUID ownerUUID;
 public UUID muxi$hatchOwner(){return ownerUUID;}
 @WrapMethod(method="tickEgg") private static void muxi$hatch(Level level,BlockPos pos,BlockState state,@Coerce Object egg,Operation<Void> original){if(((PlayerOwnedHatch)egg).muxi$hatchOwner()!=null){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(level,pos,state,egg);}}else original.call(level,pos,state,egg);}
}
