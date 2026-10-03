package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo @Mixin(targets="net.muxigame.core.compat.shaders.ShaderProgramBinary",remap=false)
public class TraceBinaryKey {
 @WrapMethod(method="packHash(Ljava/nio/file/Path;)Ljava/lang/String;",remap=false)
 private static String qa$packHash(java.nio.file.Path path,Operation<String> original){
  if(!Trace.ready)return original.call(path);
  Trace.begin("binaryPackHash");try{return original.call(path);}finally{Trace.end("binaryPackHash");}
 }
 @WrapMethod(method="hash(Ljava/lang/String;)Ljava/lang/String;",remap=false)
 private static String qa$sourceHash(String value,Operation<String> original){
  if(!Trace.ready)return original.call(value);
  Trace.begin("binarySourceHash");try{return original.call(value);}finally{Trace.end("binarySourceHash");}
 }
 @WrapOperation(method="request(ILjava/util/Map;Ljava/util/Map;Ljava/lang/String;)Lnet/muxigame/core/compat/shaders/ShaderProgramBinary$Request;",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glGetShaderi(II)I",remap=false),require=3,remap=false)
 private static int qa$shaderQuery(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  String stage=pname==0x8B81?"binaryKeyCompileStatus":"binaryKeyShaderMetadata";
  Trace.begin(stage);try{return original.call(id,pname);}finally{Trace.end(stage);}
 }
 @WrapOperation(method="request(ILjava/util/Map;Ljava/util/Map;Ljava/lang/String;)Lnet/muxigame/core/compat/shaders/ShaderProgramBinary$Request;",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glGetProgrami(II)I",remap=false),require=3,remap=false)
 private static int qa$programQuery(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  Trace.begin("binaryKeyProgramMetadata");try{return original.call(id,pname);}finally{Trace.end("binaryKeyProgramMetadata");}
 }
 @WrapOperation(method="request(ILjava/util/Map;Ljava/util/Map;Ljava/lang/String;)Lnet/muxigame/core/compat/shaders/ShaderProgramBinary$Request;",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glGetShaderSource(I)Ljava/lang/String;",remap=false),require=1,remap=false)
 private static String qa$sourceQuery(int id,Operation<String> original){
  if(!Trace.ready)return original.call(id);
  Trace.begin("binaryKeySourceQuery");try{return original.call(id);}finally{Trace.end("binaryKeySourceQuery");}
 }
}
