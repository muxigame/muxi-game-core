package net.muxigame.core.taskssmoke.mixin;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Test-only diagnostics, without changing the connection's failure handling. */
@Mixin(Connection.class)
public abstract class ConnectionDiagnosticMixin {
    @Inject(method="exceptionCaught",at=@At("HEAD"))
    private void muxi$networkException(ChannelHandlerContext context,Throwable error,CallbackInfo callback) {
        System.err.println("QA_NETWORK_EXCEPTION "+error);error.printStackTrace();
    }
    @Inject(method="disconnect",at=@At("HEAD"))
    private void muxi$disconnect(Component reason,CallbackInfo callback) {
        new Exception("QA_DISCONNECT "+reason.getString()).printStackTrace();
    }
}
