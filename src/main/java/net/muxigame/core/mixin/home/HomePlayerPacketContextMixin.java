package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HomePlayerPacketContextMixin {
 @WrapMethod(method={"handleUseItemOn","handleUseItem","handleInteract"}) private void muxi$player(@Coerce Object packet,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(packet);}}
}
