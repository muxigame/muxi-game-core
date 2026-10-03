package net.muxigame.inputlinkqa.mixin;
import net.muxigame.inputlinkqa.*;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.minigames.GameRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRuntime.class)
public abstract class GameRequestProbeMixin {
    @Inject(method="request",at=@At("HEAD"))
    private void probe(ServerPlayer p,String game,String action,String value,CallbackInfo ci){if(ProbeFiles.enabled()&&game.equals("outbreak")&&action.equals("interact")&&value.isEmpty())ProbeCounters.received.incrementAndGet();}
}
