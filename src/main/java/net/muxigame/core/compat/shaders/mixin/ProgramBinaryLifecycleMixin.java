package net.muxigame.core.compat.shaders.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.muxigame.core.compat.shaders.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.concurrent.CompletableFuture;
@Mixin(Minecraft.class)
public class ProgramBinaryLifecycleMixin {
 @Inject(method="run",at=@At("HEAD")) private void muxi$start(CallbackInfo ci){try{ShaderProgramBinary.preload();ShaderBinaryBootstrap.ready=true;}catch(LinkageError|RuntimeException|OutOfMemoryError failure){ShaderBinaryBootstrap.ready=false;}}
 @Inject(method="run",at=@At("RETURN")) private void muxi$stop(CallbackInfo ci){if(ShaderBinaryBootstrap.ready){ShaderBinaryBootstrap.ready=false;ShaderProgramBinary.close();}}
 @Inject(method="close",at=@At("HEAD")) private void muxi$shutdown(CallbackInfo ci){if(ShaderBinaryBootstrap.ready){ShaderBinaryBootstrap.ready=false;ShaderProgramBinary.close();}}
 @Inject(method="updateLevelInEngines",at=@At("RETURN")) private void muxi$leave(ClientLevel level,CallbackInfo ci){if(level==null&&ShaderBinaryBootstrap.ready)ShaderProgramBinary.release();}
 @Inject(method="reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"))
 private void muxi$resourcesBegin(CallbackInfoReturnable<CompletableFuture<Void>> cir){if(ShaderBinaryBootstrap.ready){ShaderProgramBinary.invalidate(true);Minecraft mc=(Minecraft)(Object)this;if(mc.isSameThread())DimensionShaderSwap.genuineReload();else mc.execute(DimensionShaderSwap::genuineReload);}}
 @Inject(method="reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"))
 private void muxi$resourcesEnd(CallbackInfoReturnable<CompletableFuture<Void>> cir){if(ShaderBinaryBootstrap.ready&&cir.getReturnValue()!=null)cir.getReturnValue().whenComplete((value,error)->ShaderProgramBinary.invalidate(true));}
}
