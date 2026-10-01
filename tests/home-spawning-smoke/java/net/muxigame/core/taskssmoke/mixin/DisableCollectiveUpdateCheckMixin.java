package net.muxigame.core.taskssmoke.mixin;

import java.net.http.HttpClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full-pack lab only: disable the unrelated update checker without constructing its HTTP selector. */
@Mixin(targets="com.natamus.collective_common_neoforge.check.RegisterMod",remap=false)
public abstract class DisableCollectiveUpdateCheckMixin {
    @Redirect(method="<clinit>",at=@At(value="INVOKE",target="Ljava/net/http/HttpClient$Builder;build()Ljava/net/http/HttpClient;"))
    private static HttpClient muxi$noUpdateHttpInitialization(HttpClient.Builder ignored) { return null; }

    // The skipped HTTP client must never be used, regardless of default config loading order.
    @Inject(method="register",at=@At("HEAD"),cancellable=true)
    private static void muxi$noUpdateChecks(String name, String author, String version, String minecraftVersion,
                                          CallbackInfo callback) { callback.cancel(); }
}
