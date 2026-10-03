"""CPU-only lifecycle checks against actual DimensionShaderSwap source and controlled vendor doubles.
No Minecraft is launched. The doubles model the audited outer + constructor macro calls,
including their counter/mark side effects. They do not replace native mixin/provider validation.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
STUBS = {
 'com/euphoriapatches/euphoria_patcher/integration/DefineHelper.java': 'package com.euphoriapatches.euphoria_patcher.integration;import java.util.*;public class DefineHelper {private static int injectCount;public static final List<String> effects=new ArrayList<>();public static void seed(int count){injectCount=count;effects.clear();}public static int count(){return injectCount;}public static int next(){int value=++injectCount;effects.add("defines:"+value);effects.add("scheduleSettingsMark:"+value);return value;}}',
 'net/irisshaders/iris/shaderpack/IrisDefines.java': 'package net.irisshaders.iris.shaderpack;import java.util.*;import net.irisshaders.iris.gl.shader.StandardMacros;public class IrisDefines {public static List<StandardMacros.Pair> createIrisReplacements(){return StandardMacros.createStandardEnvironmentDefines();}}',
 'org/slf4j/Logger.java': 'package org.slf4j; public interface Logger { default void info(String s,Object a){} default void warn(String s,Object a){} }',
 'com/mojang/logging/LogUtils.java': 'package com.mojang.logging; public class LogUtils {public static org.slf4j.Logger getLogger(){return new org.slf4j.Logger(){};}}',
 'net/minecraft/client/multiplayer/ClientLevel.java': 'package net.minecraft.client.multiplayer; public class ClientLevel {private final String id; public ClientLevel(String id){this.id=id;} public Key dimension(){return new Key(id);} public record Key(String id){public String location(){return id;}}}',
 'net/minecraft/client/Minecraft.java': 'package net.minecraft.client; public class Minecraft {private static final Minecraft INSTANCE=new Minecraft(); public static String euphoriaPatcher$lastDimension; public net.minecraft.client.multiplayer.ClientLevel level; public static Minecraft getInstance(){return INSTANCE;} public static boolean lambda$onDimensionChange$euphoria_patcher$0(){return net.muxigame.core.compat.shaders.DimensionShaderSwap.consumeDimensionReload();}}',
 'net/irisshaders/iris/Iris.java': 'package net.irisshaders.iris; public class Iris {public static Object currentPack; public static boolean enabled=true,euphoria=true; public static final Manager manager=new Manager(); public static Manager getPipelineManager(){return manager;} public static Config getIrisConfig(){return new Config();} public static class Config {public boolean areShadersEnabled(){return enabled;}} public static class Manager {public int destroys;public boolean fail; public void destroyPipeline(){destroys++;if(fail)throw new IllegalStateException("destroy failed");}}}',
 'net/irisshaders/iris/shaderpack/ShaderPack.java': 'package net.irisshaders.iris.shaderpack; import java.util.*;import java.nio.file.*;import net.muxigame.core.compat.shaders.*;import net.irisshaders.iris.gl.shader.StandardMacros.Pair; public class ShaderPack implements ShaderPackSourceCarrier {public static int builds;public static boolean fail;private final Source source;public ShaderPack(Source source){this.source=source;}public ShaderPack(Path root,Map<String,String> options,List<Pair> defines,boolean zip){builds++;var merged=new ArrayList<Pair>(defines);merged.addAll(IrisDefines.createIrisReplacements());if(fail)throw new IllegalStateException("pack failed");var map=new HashMap<String,String>();for(var p:merged)map.put(p.key(),p.value());source=new Source(root,options,zip,map);DimensionShaderSwap.captured(root,options,merged,zip,this);}public Source muxi$shaderPackSource(){return source;}}',
 'net/irisshaders/iris/gl/shader/StandardMacros.java': 'package net.irisshaders.iris.gl.shader;import java.util.*;public class StandardMacros {public static int calls;public static boolean invalid;public record Pair(String key,String value){}public static List<Pair> createStandardEnvironmentDefines(){calls++;int count=com.euphoriapatches.euphoria_patcher.integration.DefineHelper.next();String d=com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics.getCurrentDimensionStatic().toUpperCase(Locale.ROOT);var values=new ArrayList<Pair>();values.add(new Pair("CURRENT_EUPHORIA_PATCHES_DIMENSION_"+d,"1"));if(invalid||count<=2)values.add(new Pair("EUPHORIA_PATCHES_FIRST_LOADED","1"));return values;}}',
 'com/euphoriapatches/euphoria_patcher/util/mod/ModLoaderSpecifics.java': 'package com.euphoriapatches.euphoria_patcher.util.mod;public class ModLoaderSpecifics {public static String getCurrentDimensionStatic(){return net.minecraft.client.Minecraft.getInstance().level.dimension().location().replace("minecraft:","").replace(":","_");}}',
 'com/euphoriapatches/euphoria_patcher/integration/ShaderLoader.java': 'package com.euphoriapatches.euphoria_patcher.integration;public class ShaderLoader {public static java.nio.file.Path getCurrentShaderpackPath(){return java.nio.file.Path.of("pack");}}',
 'com/euphoriapatches/euphoria_patcher/EuphoriaPatcher.java': 'package com.euphoriapatches.euphoria_patcher;public class EuphoriaPatcher {public static EuphoriaPatcher getInstance(){return new EuphoriaPatcher();}public Detector getShaderDetector(){return new Detector();}public static class Detector {public boolean isEuphoriaPatchesShader(java.nio.file.Path p){return net.irisshaders.iris.Iris.euphoria;}}}',
}

# Model the pinned constructor's ImmutableList signature and in-place effective macro merge.
STUBS['com/google/common/collect/ImmutableList.java']='package com.google.common.collect;import java.util.*;public class ImmutableList<E> extends ArrayList<E>{private ImmutableList(Collection<? extends E> c){super(c);}public static <E> ImmutableList<E> copyOf(Collection<? extends E> c){return new ImmutableList<>(c);}}'
STUBS['net/irisshaders/iris/helpers/StringPair.java']='package net.irisshaders.iris.helpers;public class StringPair{private final String k,v;public StringPair(String k,String v){this.k=k;this.v=v;}public String key(){return k;}public String value(){return v;}}'
STUBS['net/irisshaders/iris/gl/shader/StandardMacros.java']=STUBS['net/irisshaders/iris/gl/shader/StandardMacros.java'].replace('public record Pair(String key,String value){}','public static class Pair extends net.irisshaders.iris.helpers.StringPair{public Pair(String k,String v){super(k,v);}}')
STUBS['net/irisshaders/iris/shaderpack/ShaderPack.java']=STUBS['net/irisshaders/iris/shaderpack/ShaderPack.java'].replace('public ShaderPack(Path root,Map<String,String> options,List<Pair> defines,boolean zip){','public ShaderPack(Path root,Map<String,String> options,com.google.common.collect.ImmutableList<net.irisshaders.iris.helpers.StringPair> defines,boolean zip){this(root,options,new ArrayList<net.irisshaders.iris.helpers.StringPair>(defines),zip);}public ShaderPack(Path root,Map<String,String> options,List<? extends net.irisshaders.iris.helpers.StringPair> defines,boolean zip){').replace('new ArrayList<Pair>(defines)','new ArrayList<net.irisshaders.iris.helpers.StringPair>(defines)')

STUBS['net/irisshaders/iris/gl/shader/StandardMacros.java']=STUBS['net/irisshaders/iris/gl/shader/StandardMacros.java'].replace('public static boolean invalid;','public static boolean invalid,wrongDimension;').replace('var values=new ArrayList<Pair>();','if(wrongDimension)d="WRONG";var values=new ArrayList<Pair>();')

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--javac',default=shutil.which('javac'))
 parser.add_argument('--java',default=shutil.which('java'))
 args=parser.parse_args()
 if not args.javac or not args.java: parser.error('Java 21 javac/java required')
 with tempfile.TemporaryDirectory(prefix='muxi-dimension-swap-') as folder:
  output=Path(folder);sources=[]
  for name,body in STUBS.items():
   path=output/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body,encoding='utf-8');sources.append(path)
  base=ROOT/'src/main/java/net/muxigame/core/compat/shaders'
  sources += [base/(name+'.java') for name in ['DimensionShaderSwap','ShaderPackSourceCarrier','ShaderBinaryBootstrap']]
  sources.append(ROOT/'tests/shaders/DimensionShaderSwapTest.java')
  classes=output/'classes';classes.mkdir()
  subprocess.run([args.javac,'-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True)
  subprocess.run([args.java,'-cp',str(classes),'DimensionShaderSwapTest'],check=True)
  # Independently verify a missing vendor field takes the native route without a macro probe.
  counter=output/'com/euphoriapatches/euphoria_patcher/integration/DefineHelper.java'
  counter.write_text(counter.read_text(encoding='utf-8').replace('injectCount','unknownCount'),encoding='utf-8')
  subprocess.run([args.javac,'-encoding','UTF-8','-d',str(classes),str(counter)],check=True)
  subprocess.run([args.java,'-cp',str(classes),'DimensionShaderSwapTest','missingCounter'],check=True)
if __name__=='__main__':main()
