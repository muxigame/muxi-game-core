package net.muxigame.transferprobe.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerTransferMixin {
    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"))
    private void probe$begin(DimensionTransition transition, CallbackInfoReturnable<Entity> ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (player.serverLevel() == transition.newLevel()) return;
        Trace.begin("server", player.connection, player.serverLevel().dimension().location().toString(),
                transition.newLevel().dimension().location().toString());
    }
    @Inject(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
    private void probe$end(DimensionTransition transition, CallbackInfoReturnable<Entity> ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        Trace.event("server", Trace.server(player.connection), "server_transfer_return", true,
                "accepted", ci.getReturnValue() != null);
    }
}
