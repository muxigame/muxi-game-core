package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

import net.muxigame.core.compat.shaders.ShaderBinaryStore;
/** get/put are inclusive CPU+filesystem scopes; get also includes RAM hits. */
@Pseudo @Mixin(targets="net.muxigame.core.compat.shaders.ShaderBinaryStore",remap=false)
public class TraceBinaryStore {
 @WrapMethod(method="get(Ljava/lang/String;)Lnet/muxigame/core/compat/shaders/ShaderBinaryStore$Binary;",remap=false)
 private ShaderBinaryStore.Binary qa$read(String key,Operation<ShaderBinaryStore.Binary> original){
  if(!Trace.ready)return original.call(key);
  Trace.begin("binaryDiskRead");try{return original.call(key);}finally{Trace.end("binaryDiskRead");}
 }
 @WrapMethod(method="put(Ljava/lang/String;I[B)V",remap=false)
 private void qa$write(String key,int format,byte[] bytes,Operation<Void> original){
  if(!Trace.ready){original.call(key,format,bytes);return;}
  Trace.begin("binaryDiskWrite");try{original.call(key,format,bytes);}finally{Trace.end("binaryDiskWrite");}
 }
 @WrapMethod(method="trimDisk()V",remap=false)
 private void qa$trim(Operation<Void> original){
  if(!Trace.ready){original.call();return;}
  Trace.begin("binaryDiskTrim");try{original.call();}finally{Trace.end("binaryDiskTrim");}
 }
 @WrapMethod(method="digest([B)Ljava/lang/String;",remap=false)
 private static String qa$digest(byte[] bytes,Operation<String> original){
  if(!Trace.ready)return original.call(bytes);
  Trace.begin("binaryDigest");try{return original.call(bytes);}finally{Trace.end("binaryDigest");}
 }
}
