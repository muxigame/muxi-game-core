package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
@Mixin(net.minecraft.server.level.ServerLevel.class)
public abstract class HomeLightningTrapMixin {
 @WrapOperation(method="tickChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;")) private Entity muxi$lightning(EntityType<?> type,Level level,Operation<Entity> original){return type==EntityType.SKELETON_HORSE&&HomeSpawningFeature.protectedHome(level)?null:original.call(type,level);}
}
