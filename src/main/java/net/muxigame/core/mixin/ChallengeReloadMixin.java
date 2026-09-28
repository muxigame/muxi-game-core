package net.muxigame.core.mixin;

import net.muxigame.core.client.challenge.ChallengeClient;
import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses TaCZ's actual rebound reload key; ordinary reloading is untouched outside a supply cabinet. */
@Pseudo
@Mixin(targets="com.tacz.guns.client.input.ReloadKey",remap=false)
public abstract class ChallengeReloadMixin {
    @Inject(method="onReloadPress",at=@At("HEAD"),cancellable=true)
    private static void muxi$refill(InputEvent.Key event,CallbackInfo ci){
        if(event.getAction()==1 /* GLFW_PRESS */ && com.tacz.guns.client.input.ReloadKey.RELOAD_KEY.matches(event.getKey(),event.getScanCode()) && ChallengeClient.refillNearby())ci.cancel();
    }
}
