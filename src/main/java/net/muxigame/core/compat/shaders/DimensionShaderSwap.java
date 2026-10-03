package net.muxigame.core.compat.shaders;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

/** Render-thread-only compatibility for the pinned Iris 1.8 / Euphoria 1.10 stack.
 * The parsed pack is rebuilt with the target dimension's macros, never with stale source macros.
 * Like upstream Euphoria's lean refresh, this still destroys/rebuilds GPU pipelines normally.
 * It does not change terrain readiness, receiving screens, server rules, or disk caches.
 */
public final class DimensionShaderSwap {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> DIMENSIONS=Set.of("minecraft:overworld","muxi_game_core:overworld","muxi_game_core:adventure");
    private static final LinkedHashMap<String,Object> PACKS=new LinkedHashMap<>(4,.75f,true);
    private static Path root;
    private static Map<String,String> options=Map.of();
    private static boolean zipped,building;
    private static Class<?> packClass;
    private static ClientLevel pendingLevel,handledLevel;
    private static String pendingDimension;
    private static boolean successful,reconnecting,firstEntry;
    private static final Set<String> FIRST_HINTS=Set.of("EUPHORIA_PATCHES_FIRST_LOADED","NEW_EUPHORIA_PATCHES_UPDATE","NEXT_EUPHORIA_PATCHES_VERSION");
    private static final Map<String,Object> last=new LinkedHashMap<>();
    private DimensionShaderSwap() {}
    private static String dimension(ClientLevel level){return level==null?null:level.dimension().location().toString();}
    private static Class<?> iris()throws ClassNotFoundException{return Class.forName("net.irisshaders.iris.Iris");}
    private static Object invoke(Class<?> type,String method)throws ReflectiveOperationException{return type.getMethod(method).invoke(null);}
    private static boolean euphoriaPack()throws ReflectiveOperationException{
        Object instance=invoke(Class.forName("com.euphoriapatches.euphoria_patcher.EuphoriaPatcher"),"getInstance");
        Object detector=instance.getClass().getMethod("getShaderDetector").invoke(instance);
        Object path=invoke(Class.forName("com.euphoriapatches.euphoria_patcher.integration.ShaderLoader"),"getCurrentShaderpackPath");
        return path!=null&&(Boolean)detector.getClass().getMethod("isEuphoriaPatchesShader",Path.class).invoke(detector,path);
    }
    private static Set<String> macroKeys(Object defines)throws ReflectiveOperationException{
        Set<String> keys=new HashSet<>();for(Object pair:(Iterable<?>)defines)keys.add((String)pair.getClass().getMethod("key").invoke(pair));return keys;
    }

    // Read the pinned vendor's own state; a first-ever setLevel has no reload callback.
    private static boolean reconnectEligible(){return entryEligible(false);}
    private static boolean entryEligible(boolean first){
        if(!ShaderBinaryBootstrap.owner("dimension-reconnect"))return false;
        try{
            Field lastDimension=Minecraft.class.getDeclaredField("euphoriaPatcher$lastDimension");
            if(!Modifier.isStatic(lastDimension.getModifiers())||lastDimension.getType()!=String.class)return false;
            lastDimension.setAccessible(true);
            if((lastDimension.get(null)==null)!=first)return false;
            Field pack=iris().getDeclaredField("currentPack");pack.setAccessible(true);
            if(!(pack.get(null) instanceof ShaderPackSourceCarrier carrier))return false;
            var source=carrier.muxi$shaderPackSource();
            if(source==null||source.root()==null||source.defines().isEmpty())return false;
            // Generating defines changes vendor state. Do not probe across its first-load
            // threshold: a failed attempt must not consume the native FIRST_LOADED window.
            Field count=Class.forName("com.euphoriapatches.euphoria_patcher.integration.DefineHelper").getDeclaredField("injectCount");
            if(!Modifier.isStatic(count.getModifiers())||count.getType()!=int.class)return false;
            count.setAccessible(true);
            return count.getInt(null)>=2;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return false;}
    }
    private static boolean validMacros(Set<String> keys,String expected){
        return keys.contains(expected)&&keys.stream().filter(k->k.startsWith("CURRENT_EUPHORIA_PATCHES_DIMENSION_")).count()==1
            &&!keys.contains("EUPHORIA_PATCHES_FIRST_LOADED");
    }
    public static void changingLevel(ClientLevel next){
        ClientLevel old=Minecraft.getInstance().level;
        pendingLevel=null;pendingDimension=null;successful=false;handledLevel=null;reconnecting=false;firstEntry=false;
        if(next==null){clear();return;}
        String from=dimension(old),to=dimension(next);
        firstEntry=from==null&&to!=null&&to.startsWith("muxi_game_core:")&&DIMENSIONS.contains(to)&&entryEligible(true);
        reconnecting=from==null&&DIMENSIONS.contains(to)&&reconnectEligible();
        if(firstEntry||reconnecting||(from!=null&&!from.equals(to)&&DIMENSIONS.contains(from)&&DIMENSIONS.contains(to))){
            pendingLevel=next;pendingDimension=to;
        }
    }
    public static void clear(){root=null;packClass=null;options=Map.of();PACKS.clear();pendingLevel=null;handledLevel=null;pendingDimension=null;successful=false;reconnecting=false;firstEntry=false;last.clear();}
    public static void genuineReload(){if(!building)clear();}
    public static void captured(Path path,Map<String,String> changed,Object defines,boolean zip,Object pack){
        if(building)return;
        PACKS.clear();root=path;options=changed==null?Map.of():new HashMap<>(changed);zipped=zip;packClass=pack.getClass();successful=false;handledLevel=null;
        try{String dim=dimension(Minecraft.getInstance().level);if(dim!=null&&euphoriaPack()&&!macroKeys(defines).contains("EUPHORIA_PATCHES_FIRST_LOADED"))PACKS.put(dim,pack);}catch(ReflectiveOperationException ignored){}
    }
    public static void beforePipeline(){
        if(pendingLevel==null||pendingLevel!=Minecraft.getInstance().level||handledLevel==pendingLevel)return;
        if(firstEntry){
            try{refreshFirstEntry();}finally{pendingLevel=null;pendingDimension=null;firstEntry=false;successful=false;}
        }else refresh();
    }
    /** Only the first custom entry: no vendor reload callback exists to consume. */
    private static void refreshFirstEntry(){
        handledLevel=pendingLevel;
        try{
            if(!ShaderBinaryBootstrap.owner("dimension-reconnect")||!euphoriaPack())return;
            Class<?> iris=iris();
            Object config=invoke(iris,"getIrisConfig");
            if(!(Boolean)config.getClass().getMethod("areShadersEnabled").invoke(config))return;
            Field field=iris.getDeclaredField("currentPack");field.setAccessible(true);
            Object current=field.get(null);
            if(!(current instanceof ShaderPackSourceCarrier carrier))return;
            var source=carrier.muxi$shaderPackSource();
            if(source==null||source.root()==null||source.defines().isEmpty()
                    ||source.defines().keySet().stream().filter(k->k.startsWith("CURRENT_EUPHORIA_PATCHES_DIMENSION_")).count()!=1)return;
            Field count=Class.forName("com.euphoriapatches.euphoria_patcher.integration.DefineHelper").getDeclaredField("injectCount");
            count.setAccessible(true);if(count.getInt(null)<2)return;
            String expected="CURRENT_EUPHORIA_PATCHES_DIMENSION_"+String.valueOf(invoke(Class.forName("com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics"),"getCurrentDimensionStatic")).toUpperCase(Locale.ROOT);
            if(dimensionMatches(source.defines().keySet(),expected))return;
            // Do not carry stale environment/presence macros. The normal constructor appends
            // its native environment factory exactly once, while these hints retain first-use UI.
            Class<?> pair=Class.forName("net.irisshaders.iris.helpers.StringPair");
            var hints=new ArrayList<Object>();
            for(String key:FIRST_HINTS)if(source.defines().containsKey(key))
                hints.add(pair.getConstructor(String.class,String.class).newInstance(key,source.defines().get(key)));
            Class<?> list=Class.forName("com.google.common.collect.ImmutableList");
            Object defines=list.getMethod("copyOf",Collection.class).invoke(null,hints);
            Constructor<?> ctor=current.getClass().getConstructor(Path.class,Map.class,list,boolean.class);
            Object target;
            building=true;
            try{target=ctor.newInstance(source.root(),new HashMap<>(source.options()),defines,source.zipped());}finally{building=false;}
            if(!(target instanceof ShaderPackSourceCarrier result))return;
            var actual=result.muxi$shaderPackSource();
            if(actual==null||!Objects.equals(source.root(),actual.root())||!source.options().equals(actual.options())
                    ||source.zipped()!=actual.zipped()||!dimensionMatches(actual.defines().keySet(),expected))return;
            for(String key:FIRST_HINTS)if(!Objects.equals(source.defines().get(key),actual.defines().get(key)))return;
            Object manager=invoke(iris,"getPipelineManager");
            manager.getClass().getMethod("destroyPipeline").invoke(manager);
            field.set(null,target);
            root=source.root();options=new HashMap<>(source.options());zipped=source.zipped();packClass=target.getClass();
            PACKS.clear(); // FIRST_LOADED packs must not enter the normal per-dimension reuse cache.
            last.clear();last.put("dimension",pendingDimension);last.put("dimensionMacro",expected);
            last.put("firstEntry",true);last.put("preservedFirstHints",true);
            LOG.info("MUXI_DIMENSION_SHADER_SWAP {}",last);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            LOG.warn("Initial dimension shader refresh unavailable; retaining native behavior: {}",failure.getClass().getSimpleName());
        }
    }
    private static boolean dimensionMatches(Set<String> keys,String expected){
        return keys.contains(expected)&&keys.stream().filter(k->k.startsWith("CURRENT_EUPHORIA_PATCHES_DIMENSION_")).count()==1;
    }
    private static boolean refresh(){
        // Any failure, including a macro guard, is terminal for this setLevel.
        // StandardMacros advances vendor state and must not be retried each frame.
        handledLevel=pendingLevel;
        long begin=System.nanoTime();
        try{
            Class<?> iris=iris();
            Field field=iris.getDeclaredField("currentPack");field.setAccessible(true);Object current=field.get(null);
            if(root==null&&current instanceof ShaderPackSourceCarrier carrier){var source=carrier.muxi$shaderPackSource();if(source!=null){root=source.root();options=new HashMap<>(source.options());zipped=source.zipped();packClass=current.getClass();}}
            if(root==null||packClass==null||!euphoriaPack())return false;
            Object config=invoke(iris,"getIrisConfig");
            if(!(Boolean)config.getClass().getMethod("areShadersEnabled").invoke(config))return false;
            if(current==null)return false;
            boolean cached=PACKS.containsKey(pendingDimension);Object target=PACKS.get(pendingDimension);
            String dimensionMacro="CURRENT_EUPHORIA_PATCHES_DIMENSION_"+String.valueOf(invoke(Class.forName("com.euphoriapatches.euphoria_patcher.util.mod.ModLoaderSpecifics"),"getCurrentDimensionStatic")).toUpperCase(Locale.ROOT);
            boolean reusedCurrent=false;
            // An early native pack may enter refresh, but validMacros forbids reusing it.
            // Its replacement gets fresh defines here and the vendor's second set in its constructor.
            if(target==null&&reconnecting&&current instanceof ShaderPackSourceCarrier carrier){
                var source=carrier.muxi$shaderPackSource();
                if(source!=null&&Objects.equals(root,source.root())&&options.equals(source.options())&&zipped==source.zipped()
                    &&validMacros(source.defines().keySet(),dimensionMacro)){
                    target=current;reusedCurrent=true;PACKS.put(pendingDimension,target);
                }
            }
            if(target==null){
                Object defines=invoke(Class.forName("net.irisshaders.iris.gl.shader.StandardMacros"),"createStandardEnvironmentDefines");
                Set<String> keys=macroKeys(defines);
                if(!validMacros(keys,dimensionMacro))return false;
                Constructor<?> ctor=Arrays.stream(packClass.getConstructors()).filter(c->c.getParameterCount()==4&&Map.class.isAssignableFrom(c.getParameterTypes()[1])).findFirst().orElseThrow();
                building=true;
                try{target=ctor.newInstance(root,new HashMap<>(options),defines,zipped);}finally{building=false;}
                PACKS.put(pendingDimension,target);while(PACKS.size()>3)PACKS.remove(PACKS.keySet().iterator().next());
            }
            long built=System.nanoTime();
            Object manager=invoke(iris,"getPipelineManager");
            // Successful destroy is required before cancelling the vendor's reload.
            manager.getClass().getMethod("destroyPipeline").invoke(manager);
            field.set(null,target);
            handledLevel=pendingLevel;successful=true;
            last.clear();last.put("dimension",pendingDimension);last.put("cachedParsedPack",cached);last.put("parsedPacks",PACKS.size());
            last.put("dimensionMacro",dimensionMacro);last.put("reconnecting",reconnecting);last.put("reusedCurrentPack",reusedCurrent);
            last.put("packRefreshMs",(built-begin)/1e6);last.put("pipelineDestroyMs",(System.nanoTime()-built)/1e6);
            LOG.info("MUXI_DIMENSION_SHADER_SWAP {}",last);
            return true;
        }catch(ReflectiveOperationException|RuntimeException e){
            successful=false;handledLevel=pendingLevel;
            LOG.warn("Dimension shader refresh unavailable; retaining Euphoria's full reload: {}",e.toString());return false;
        }
    }
    public static boolean consumeDimensionReload(){
        if(pendingLevel==null||pendingLevel!=Minecraft.getInstance().level)return false;
        boolean dimensionCaller=StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals("net.minecraft.client.Minecraft")&&f.getMethodName().contains("lambda$onDimensionChange")&&f.getMethodName().contains("euphoria_patcher")));
        if(!dimensionCaller)return false;
        if(!successful&&handledLevel!=pendingLevel)refresh();
        if(!successful)return false;
        LOG.info("MUXI_DIMENSION_SHADER_EXTRA_RELOAD_AVOIDED {}",pendingDimension);
        // Only this completed setLevel's callback is consumed; manual reloads remain untouched.
        pendingLevel=null;pendingDimension=null;reconnecting=false;return true;
    }
    public static Map<String,Object> diagnosticSnapshot(){return Map.copyOf(last);}
    public static Map<String,Object> diagnosticLifecycleSnapshot(){return Map.of("parsedPacks",PACKS.size(),"capturedPackRoot",root!=null,"pendingLevel",pendingLevel!=null);}
}
