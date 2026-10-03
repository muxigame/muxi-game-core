package net.muxigame.binaryqa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.muxigame.binaryqa.Trace;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Nested wall elapsed only: JNI call duration is not exclusive GPU time. */
@Pseudo @Mixin(targets="net.muxigame.core.compat.shaders.ShaderProgramBinary$1",remap=false)
public class TraceBinaryDriver {
 @WrapOperation(method="capture()Lnet/muxigame/core/compat/shaders/ShaderBinaryStore$Binary;",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/ARBGetProgramBinary;glGetProgramBinary(I[I[ILjava/nio/ByteBuffer;)V",remap=false),require=1,remap=false)
 private void qa$readback(int id,int[] size,int[] format,java.nio.ByteBuffer bytes,Operation<Void> original){
  if(!Trace.ready){original.call(id,size,format,bytes);return;}
  Trace.begin("binaryDriverReadback");try{original.call(id,size,format,bytes);}finally{Trace.end("binaryDriverReadback");}
 }
 @WrapOperation(method="load(I[B)Z",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/ARBGetProgramBinary;glProgramBinary(IILjava/nio/ByteBuffer;)V",remap=false),require=1,remap=false)
 private void qa$load(int id,int format,java.nio.ByteBuffer bytes,Operation<Void> original){
  if(!Trace.ready){original.call(id,format,bytes);return;}
  Trace.begin("binaryDriverLoad");try{original.call(id,format,bytes);}finally{Trace.end("binaryDriverLoad");}
 }
 @WrapOperation(method="capture()Lnet/muxigame/core/compat/shaders/ShaderBinaryStore$Binary;",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glGetProgrami(II)I",remap=false),require=2,remap=false)
 private int qa$captureQuery(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  String stage=pname==0x8B82?"binaryCaptureLinkStatus":"binaryCaptureLengthQuery";
  Trace.begin(stage);try{return original.call(id,pname);}finally{Trace.end(stage);}
 }
 @WrapOperation(method="load(I[B)Z",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glGetProgrami(II)I",remap=false),require=1,remap=false)
 private int qa$loadStatus(int id,int pname,Operation<Integer> original){
  if(!Trace.ready)return original.call(id,pname);
  Trace.begin("binaryLoadLinkStatus");try{return original.call(id,pname);}finally{Trace.end("binaryLoadLinkStatus");}
 }
}
