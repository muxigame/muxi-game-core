package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Native Mojang query boundaries, also active with binary cache OFF. */
@Pseudo @Mixin(targets="com.mojang.blaze3d.platform.GlStateManager",remap=false)
public class TraceNativeShaderQueries {
 @WrapMethod(method="glGetProgrami(II)I",remap=false)
 private static int qa$program(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  String stage=pname==0x8B82?"nativeProgramLinkStatus":"nativeProgramQuery";
  Trace.begin(stage);try{return original.call(id,pname);}finally{Trace.end(stage);}
 }
 @WrapMethod(method="glGetShaderi(II)I",remap=false)
 private static int qa$shader(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  String stage=pname==0x8B81?"nativeShaderCompileStatus":"nativeShaderQuery";
  Trace.begin(stage);try{return original.call(id,pname);}finally{Trace.end(stage);}
 }
 @WrapMethod(method="glGetProgramInfoLog(II)Ljava/lang/String;",remap=false)
 private static String qa$programLog(int id,int length,Operation<String> original){
  if(!Trace.ready)return original.call(id,length);
  Trace.begin("nativeProgramInfoLog");try{return original.call(id,length);}finally{Trace.end("nativeProgramInfoLog");}
 }
 @WrapMethod(method="glGetShaderInfoLog(II)Ljava/lang/String;",remap=false)
 private static String qa$shaderLog(int id,int length,Operation<String> original){
  if(!Trace.ready)return original.call(id,length);
  Trace.begin("nativeShaderInfoLog");try{return original.call(id,length);}finally{Trace.end("nativeShaderInfoLog");}
 }
}
