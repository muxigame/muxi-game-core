package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.UUID;
@Pseudo @Mixin(targets="com.iafenvoy.iceandfire.entity.DragonEggEntity")
public abstract class HomeDragonEggOwnerMixin implements PlayerOwnedHatch {
 @Shadow public abstract UUID getOwnerId();
 public UUID muxi$hatchOwner(){return getOwnerId();}
}
