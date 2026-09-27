package net.muxigame.core.taskssmoke.mixin;

import java.net.InetAddress;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Test-only: no socket listener in the isolated lifecycle tests. Never shipped in Game Core. */
@Mixin(targets="net.minecraft.server.dedicated.DedicatedServer",remap=false)
public abstract class DisableNetworkMixin {
    @Redirect(method="initServer",at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void muxi$skipTestSocket(ServerConnectionListener listener,InetAddress address,int port){}
}
