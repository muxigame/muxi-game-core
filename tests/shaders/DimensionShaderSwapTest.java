import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.muxigame.core.compat.shaders.*;
public final class DimensionShaderSwapTest {
 static int checks;
 static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 static Map<String,String> macros(String dim){return Map.of("CURRENT_EUPHORIA_PATCHES_DIMENSION_"+dim,"1");}
 static void reset(){DimensionShaderSwap.clear();Minecraft.getInstance().level=null;Minecraft.euphoriaPatcher$lastDimension="overworld";ShaderBinaryBootstrap.owner("dimension-reconnect",true);Iris.enabled=true;Iris.euphoria=true;Iris.manager.destroys=0;Iris.manager.fail=false;StandardMacros.calls=0;StandardMacros.invalid=false;ShaderPack.builds=0;Iris.currentPack=new ShaderPack(new ShaderPackSourceCarrier.Source(Path.of("pack"),Map.of("quality","high"),false,macros("OVERWORLD")));}
 static ClientLevel begin(String dim){var next=new ClientLevel(dim);DimensionShaderSwap.changingLevel(next);Minecraft.getInstance().level=next;DimensionShaderSwap.beforePipeline();return next;}
 static boolean pending(){return (boolean)DimensionShaderSwap.diagnosticLifecycleSnapshot().get("pendingLevel");}
 public static void main(String[] args){
  reset();Minecraft.euphoriaPatcher$lastDimension=null;begin("minecraft:overworld");check(!pending()&&Iris.manager.destroys==0&&StandardMacros.calls==0,"first vendor callback only initializes: native pipeline unchanged");
  reset();ShaderBinaryBootstrap.owner("dimension-reconnect",false);begin("minecraft:overworld");check(!pending()&&Iris.manager.destroys==0,"unknown provider preserves native join");
  reset();Iris.currentPack=new ShaderPack(new ShaderPackSourceCarrier.Source(Path.of("pack"),Map.of(),false,Map.of("EUPHORIA_PATCHES_FIRST_LOADED","1")));begin("minecraft:overworld");check(!pending()&&StandardMacros.calls==0,"early source rejected without advancing define counter");
  reset();Object original=Iris.currentPack;begin("minecraft:overworld");check(Iris.currentPack==original&&ShaderPack.builds==0&&StandardMacros.calls==0,"matching reconnect reuses parsed native pack without generating macros");check(Iris.manager.destroys==1,"GPU pipeline still destroyed");check(!DimensionShaderSwap.consumeDimensionReload()&&pending(),"manual reload is not vendor callback");check(Minecraft.lambda$onDimensionChange$euphoria_patcher$0()&&!pending(),"matching completed vendor callback consumed once");check(!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"second callback native");
  reset();begin("muxi_game_core:adventure");check(ShaderPack.builds==1&&StandardMacros.calls==1&&Iris.manager.destroys==1,"different source rebuilt with target defines");check(((ShaderPack)Iris.currentPack).muxi$shaderPackSource().defines().containsKey("CURRENT_EUPHORIA_PATCHES_DIMENSION_MUXI_GAME_CORE_ADVENTURE"),"target pack macros correct");
  reset();StandardMacros.invalid=true;begin("muxi_game_core:adventure");for(int i=0;i<3;i++)DimensionShaderSwap.beforePipeline();check(!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"macro failure retains vendor reload");check(StandardMacros.calls==1&&ShaderPack.builds==0&&Iris.manager.destroys==0,"failed macro validation cannot retry after initial native pipeline");
  reset();Iris.manager.fail=true;begin("minecraft:overworld");DimensionShaderSwap.beforePipeline();check(!Minecraft.lambda$onDimensionChange$euphoria_patcher$0()&&Iris.manager.destroys==1,"destroy failure cannot suppress vendor or retry");
  reset();begin("minecraft:the_nether");check(!pending()&&Iris.manager.destroys==0,"unsupported reconnect native");
  reset();Iris.enabled=false;begin("minecraft:overworld");DimensionShaderSwap.beforePipeline();check(Iris.manager.destroys==0&&!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"disabled shaders retain vendor fallback");
  reset();Iris.euphoria=false;begin("minecraft:overworld");check(Iris.manager.destroys==0&&!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"other shader pack native");
  reset();begin("minecraft:overworld");DimensionShaderSwap.clear();check(!pending()&&(int)DimensionShaderSwap.diagnosticLifecycleSnapshot().get("parsedPacks")==0&&!(boolean)DimensionShaderSwap.diagnosticLifecycleSnapshot().get("capturedPackRoot"),"logout clears all static ownership");Minecraft.getInstance().level=null;begin("minecraft:overworld");check(Iris.manager.destroys==2&&(boolean)DimensionShaderSwap.diagnosticSnapshot().get("reusedCurrentPack"),"reconnect restores metadata from surviving native pack");
  DimensionShaderSwap.genuineReload();check(!pending()&&!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"genuine reload cancels pending callback consumption");
  reset();ShaderBinaryBootstrap.owner("dimension-reconnect",false);Minecraft.getInstance().level=new ClientLevel("minecraft:overworld");begin("muxi_game_core:adventure");check(ShaderPack.builds==1&&Iris.manager.destroys==1,"unknown reconnect pin preserves existing cross-dimension path");
  reset();begin("minecraft:overworld");Minecraft.getInstance().level=new ClientLevel("minecraft:overworld");check(!Minecraft.lambda$onDimensionChange$euphoria_patcher$0(),"same dimension different level cannot consume old callback");
  System.out.println("DimensionShaderSwap lifecycle checks passed: "+checks);
 }
}
