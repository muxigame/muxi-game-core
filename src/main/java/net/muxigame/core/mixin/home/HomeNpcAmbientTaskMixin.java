package net.muxigame.core.mixin.home;
import net.muxigame.core.feature.spawning.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.CompletableFuture;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
@Pseudo @Mixin(targets="noppes.npcs.NPCSpawning")
public abstract class HomeNpcAmbientTaskMixin {
 @WrapOperation(method="spawnData",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;submit(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;")) private static CompletableFuture<?> muxi$ambient(MinecraftServer server,Runnable task,Operation<CompletableFuture<?>> original){return original.call(server,HomeSpawnContext.bind(HomeSpawnContext.Origin.AMBIENT,task));}
}
