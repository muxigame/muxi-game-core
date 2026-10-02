package net.muxigame.core.compat.shaders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Length-framed content keys: no ambiguous Map.toString and no launcher-supplied fingerprint. */
public final class ShaderBinaryKey {
 public record Stage(int type,String sourceHash) {}
 private ShaderBinaryKey() {}
 private static void text(DataOutputStream out,String value)throws IOException {byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
 public static String map(Map<String,?> values) {
  try {var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(values.size());for(var entry:new TreeMap<>(values).entrySet()){text(out,entry.getKey());text(out,String.valueOf(entry.getValue()));}return ShaderBinaryStore.digest(bytes.toByteArray());}
  catch(IOException impossible){throw new IllegalStateException(impossible);}
 }
 public static String create(String family,String dimension,String driver,String pack,String options,String macros,List<Stage> sources,Map<String,Integer> attributes,Map<String,Integer> fragments) {
  try {var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);for(String part:List.of("muxi-native-program-v2",family,dimension,driver,pack,options,macros,"separable=0;transform-feedback=0"))text(out,part);
   var sorted=new ArrayList<>(sources);sorted.sort(Comparator.comparingInt(Stage::type).thenComparing(Stage::sourceHash));out.writeInt(sorted.size());for(var source:sorted){out.writeInt(source.type());text(out,source.sourceHash());}text(out,map(attributes));text(out,map(fragments));return ShaderBinaryStore.digest(bytes.toByteArray());}
  catch(IOException impossible){throw new IllegalStateException(impossible);}
 }
}
