package net.muxigame.binaryqa.mixin;
import net.minecraft.client.Minecraft;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class TraceMinecraft {
 @Inject(method="destroy",at=@At(value="INVOKE",target="Ljava/lang/System;exit(I)V",shift=At.Shift.BEFORE),require=0) private void beforeExit(CallbackInfo ci){try{net.muxigame.binaryqa.CreativeOrigins.dump();java.nio.file.Files.writeString(java.nio.file.Path.of("cache-after-native-shutdown.json"),new com.google.gson.Gson().toJson(net.muxigame.core.compat.shaders.ShaderProgramBinary.snapshot()));}catch(Exception e){e.printStackTrace();}}

 @Inject(method="run",at=@At("HEAD")) private void qa$start(CallbackInfo ci){Trace.ready=true;}
 @Inject(method="setLevel",at=@At("HEAD")) private void qa$levelBegin(CallbackInfo ci){Trace.begin("setLevel");}
 @Inject(method="setLevel",at=@At("RETURN")) private void qa$levelEnd(CallbackInfo ci){Trace.end("setLevel");}
 @Inject(method="close",at=@At("RETURN")) private void qa$end(CallbackInfo ci){try{java.nio.file.Files.writeString(java.nio.file.Path.of("native-stage-spans.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Trace.spans()));java.nio.file.Files.writeString(java.nio.file.Path.of("cache-after-process-cleanup.json"),new com.google.gson.Gson().toJson(net.muxigame.core.compat.shaders.ShaderProgramBinary.snapshot()));}catch(Exception ignored){}}
}
