package net.muxigame.core.compat.shaders;
import java.nio.file.Path;
import java.util.Map;
/** Source metadata belongs to the native ShaderPack; adds no static cache, level reference or GPU ownership. */
public interface ShaderPackSourceCarrier {
 record Source(Path root,Map<String,String> options,boolean zipped,Map<String,String> defines){
  public Source(Path root,Map<String,String> options,boolean zipped){this(root,options,zipped,Map.of());}
 }
 Source muxi$shaderPackSource();
}
