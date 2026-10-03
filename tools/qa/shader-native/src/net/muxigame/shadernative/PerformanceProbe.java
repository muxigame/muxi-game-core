package net.muxigame.shadernative;
/** Read-only compatibility for historical Core candidates and the extracted owner. */
public final class PerformanceProbe {
 public static Class<?> type(String name)throws ClassNotFoundException {
  var loader=PerformanceProbe.class.getClassLoader();
  try{return Class.forName(name,false,loader);}catch(ClassNotFoundException missing){
   if(name.startsWith("net.muxigame.core.compat.shaders.")||name.startsWith("net.muxigame.core.compat.logging."))
    return Class.forName(name.replace("net.muxigame.core.compat.","net.muxigame.performance.compat."),false,loader);
   throw missing;
  }
 }
 private PerformanceProbe(){}
}
