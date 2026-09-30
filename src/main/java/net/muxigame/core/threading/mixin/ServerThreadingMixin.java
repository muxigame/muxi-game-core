package net.muxigame.core.threading.mixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.*;
import net.muxigame.core.threading.DimensionThreads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.function.BooleanSupplier;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
@Mixin(MinecraftServer.class)
public abstract class ServerThreadingMixin {
    @WrapOperation(method="tickChildren",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getWorldArray()[Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel[] muxi$parallelWorlds(MinecraftServer server, Operation<ServerLevel[]> original, BooleanSupplier timeLeft) {
        DimensionThreads.tick(server, original.call(server), timeLeft); return new ServerLevel[0];
    }
    @Inject(method="getProfiler",at=@At("HEAD"),cancellable=true)
    private void muxi$workerProfiler(CallbackInfoReturnable<ProfilerFiller> callback) {
        if (!((MinecraftServer)(Object)this).isSameThread()) callback.setReturnValue(InactiveProfiler.INSTANCE);
    }
    @Inject(method="stopServer",at=@At("RETURN"))
    private void muxi$closeWorkers(CallbackInfo callback) { DimensionThreads.close((MinecraftServer)(Object)this); }
}
