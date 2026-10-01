package net.muxigame.core.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.muxigame.core.feature.dimensions.DimensionsFeature;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;

/** Route a genuinely new player before login packets/chunk tracking ever attach them to the home world. */
@Mixin(PlayerList.class)
public abstract class InitialPlayerSpawnMixin {
    @Redirect(method="placeNewPlayer",at=@At(value="INVOKE",
        target="Ljava/util/Optional;orElse(Ljava/lang/Object;)Ljava/lang/Object;",ordinal=1))
    private Object muxi$initialDimension(Optional<?> dimension,Object fallback,
                                         Connection connection,ServerPlayer player,CommonListenerCookie cookie) {
        Object original=dimension.isPresent()?dimension.get():fallback;
        return DimensionsFeature.needsInitialSurvival(player)?WorldDimensions.OVERWORLD:original;
    }

    @Inject(method="placeNewPlayer",at=@At(value="INVOKE",
        target="Lnet/minecraft/server/level/ServerPlayer;setServerLevel(Lnet/minecraft/server/level/ServerLevel;)V",
        shift=At.Shift.AFTER),cancellable=true)
    private void muxi$initialPosition(Connection connection,ServerPlayer player,CommonListenerCookie cookie,CallbackInfo callback) {
        if(!DimensionsFeature.needsInitialSurvival(player))return;
        if(!player.serverLevel().dimension().equals(WorldDimensions.OVERWORLD))return;
        var landing=DimensionsFeature.findInitialSurvivalLanding(player.serverLevel(),player);
        if(landing==null) {
            connection.disconnect(Component.literal("暂时找不到安全的新手出生点，请重新连接重试。"));
            callback.cancel();
            return;
        }
        DimensionsFeature.applyInitialSurvivalSpawn(player,landing);
    }
}
