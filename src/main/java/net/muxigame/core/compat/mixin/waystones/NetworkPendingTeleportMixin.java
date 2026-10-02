package net.muxigame.core.compat.mixin.waystones;

import com.mojang.datafixers.util.Either;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.waystones.network.NetworkTeleportGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Installed 21.1.42 validates pending context after chunk preparation and before XP consumption. */
@Mixin(targets="net.blay09.mods.waystones.core.WaystoneTeleportManager",remap=false)
public abstract class NetworkPendingTeleportMixin {
    @Inject(method="validateRequirements(Lnet/blay09/mods/waystones/api/WaystoneTeleportContext;)Lcom/mojang/datafixers/util/Either;",at=@At("RETURN"),cancellable=true,require=1)
    private static void muxi$validateImmediatelyBeforeCost(WaystoneTeleportContext context,
            CallbackInfoReturnable<Either<Void,WaystoneTeleportError>> cir) {
        String reason=NetworkTeleportGuard.denial(context);
        if(reason!=null)cir.setReturnValue(Either.right(new WaystoneTeleportError(Component.literal(reason))));
    }
    @Inject(method="validatePendingTeleport(Lnet/blay09/mods/waystones/api/WaystoneTeleportContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/server/MinecraftServer;)Lcom/mojang/datafixers/util/Either;",at=@At("RETURN"),cancellable=true,require=1)
    private static void muxi$validateNetwork(WaystoneTeleportContext context,Level level,MinecraftServer server,
            CallbackInfoReturnable<Either<Void,WaystoneTeleportError>> cir) {
        String reason=NetworkTeleportGuard.denial(context,true);
        if(reason!=null)cir.setReturnValue(Either.right(new WaystoneTeleportError(Component.literal(reason))));
    }
}
