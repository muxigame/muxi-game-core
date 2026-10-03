import java.lang.reflect.*;import java.util.*;import com.google.gson.*;
public final class ShaderDiagnosticTest {
 private static int checks;private static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  var method=Class.forName("net.muxigame.shadernative.ClientQA",false,ShaderDiagnosticTest.class.getClassLoader()).getDeclaredMethod("verifyMacro",JsonObject.class,Map.class,String.class);method.setAccessible(true);
  var valid=new JsonObject();method.invoke(null,valid,Map.of("CURRENT_EUPHORIA_PATCHES_DIMENSION_MUXI_GAME_CORE_OVERWORLD","1"),"muxi_game_core_overworld");check(valid.get("expectedMacro").getAsString().equals("CURRENT_EUPHORIA_PATCHES_DIMENSION_MUXI_GAME_CORE_OVERWORLD"));check(valid.getAsJsonArray("actualDimensionMacroKeys").size()==1);check(!valid.get("FIRST_LOADED").getAsBoolean());
  for(Map<String,String> defines:List.of(Map.of("EUPHORIA_PATCHES_FIRST_LOADED","1"),Map.of("CURRENT_EUPHORIA_PATCHES_DIMENSION_OVERWORLD","1"),Map.of("CURRENT_EUPHORIA_PATCHES_DIMENSION_MUXI_GAME_CORE_OVERWORLD","1","CURRENT_EUPHORIA_PATCHES_DIMENSION_OVERWORLD","1"))){
   var diagnostic=new JsonObject();try{method.invoke(null,diagnostic,defines,"muxi_game_core_overworld");throw new AssertionError("Macro mismatch was accepted");}catch(InvocationTargetException failure){check(failure.getCause() instanceof IllegalStateException);check(failure.getCause().getMessage().equals("Incorrect dimension macro"));}
   check(diagnostic.has("expectedMacro"));check(diagnostic.has("actualDimensionMacroKeys"));check(diagnostic.has("nativeDimension"));check(diagnostic.get("FIRST_LOADED").getAsBoolean()==defines.containsKey("EUPHORIA_PATCHES_FIRST_LOADED"));
  }
  System.out.println("PASS "+checks+" macro diagnostic-before-assert assertions; mismatch still rejects");
 }
}
