package net.muxigame.core.compat.shaders;
import java.util.concurrent.ConcurrentHashMap;
/** No Minecraft, logging, cache, or GL dependencies: safe during class transformation. */
public final class ShaderBinaryBootstrap {
 public static volatile boolean ready;
 private static final ConcurrentHashMap<String,Boolean> owners=new ConcurrentHashMap<>();
 public static void owner(String name,boolean value){owners.put(name,value);}
 public static boolean owner(String name){return Boolean.TRUE.equals(owners.get(name));}
 public static java.util.Map<String,Boolean> owners(){return java.util.Map.copyOf(owners);}
 private ShaderBinaryBootstrap(){}
}
