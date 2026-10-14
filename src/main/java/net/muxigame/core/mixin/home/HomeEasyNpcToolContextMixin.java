package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerPlayer;
@Pseudo @Mixin(targets={"de.markusbordihn.easynpc.configui.network.message.server.SpawnPresetMessage","de.markusbordihn.easynpc.configui.network.message.server.RespawnNPCMessage"})
public abstract class HomeEasyNpcToolContextMixin {
 @WrapMethod(method="handleServer") private void muxi$tool(ServerPlayer player,Operation<Void> original){try(var scope=HomeSpawnContext.enter(HomeSpawnContext.Origin.PLAYER)){original.call(player);}}
}
