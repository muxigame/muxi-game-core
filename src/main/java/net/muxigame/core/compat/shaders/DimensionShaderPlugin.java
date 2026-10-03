package net.muxigame.core.compat.shaders;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
import java.security.MessageDigest;
/** Optional and client-only; do not compete with newer Euphoria's native lean refresh. */
public final class DimensionShaderPlugin implements IMixinConfigPlugin {
    // Independently gated: unknown versions retain the existing cross-dimension path.
    private static final Map<String,String> RECONNECT_PINS=Map.ofEntries(
        Map.entry("com.euphoriapatches.euphoria_patcher.neoforge.mixin.ReloadShadersOnDimensionChangeMixin","b40b7c2bd70dfebaabb1d97ce5e72c9b9f28c7883827dcf3387d3212c4244ddf"),
        Map.entry("com.euphoriapatches.euphoria_patcher.integration.DefineHelper","651e28fe9881d48f79f64f1f03304cde613197bce7f2ecf07c4cb48757ab3d5c"),
        Map.entry("com.euphoriapatches.euphoria_patcher.neoforge.mixin.IrisModernStandardMacrosMixin","e54a16823c2a619aa1bea2fc2cd06a9f462f44a54a49c2ff351d1bdc4bebaacf"),
        Map.entry("com.euphoriapatches.euphoria_patcher.neoforge.NeoforgeModLoaderSpecifics","02c8e4ffb843cc9e1bef83f80c605c45e8daeacb28589424043a69bbfe1c578a"),
        Map.entry("com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager","50347e202a562db91753eae6da218c3a2e490806862fa0f85388d9a951aea7f8"),
        Map.entry("net.irisshaders.iris.mixin.MixinMinecraft_PipelineManagement","d9a7cf9356856b1722f5b8cba89639e76f9f7647a9b7b39223a5482adaca67a8"),
        Map.entry("net.irisshaders.iris.gl.shader.StandardMacros","0492c44ee26513039139c8fdc9f669cbccaefbdcdd648c32f466d1b254f63892"),
        Map.entry("net.irisshaders.iris.shaderpack.ShaderPack","b5e8548bf3fd6a2d4fa352bf710618ef7216cb0129ca86b26a21964a402a0630"),
        Map.entry("net.irisshaders.iris.shaderpack.IrisDefines","0cef2f24a4d2b498b2cc4256db1b4423ca6a516bdceced868304f11bfd07033d"),
        Map.entry("net.irisshaders.iris.pipeline.PipelineManager","9688ac772f3d6f1db13162d560f7c8a8afe0e520af1c7401621e386467b57b4f"));
    private static boolean pin(String name,String expected){
        try(var in=DimensionShaderPlugin.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){
            return in!=null&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(expected);
        }catch(Exception|LinkageError unavailable){return false;}
    }
    public void onLoad(String pkg){
        ShaderBinaryBootstrap.owner("dimension-reconnect",RECONNECT_PINS.entrySet().stream().allMatch(e->pin(e.getKey(),e.getValue())));
    }
    public String getRefMapperConfig(){return null;}
    private boolean exists(String name){try{return MixinService.getService().getBytecodeProvider().getClassNode(name)!=null;}catch(Exception e){return false;}}
    public boolean shouldApplyMixin(String target,String mixin){return !Boolean.getBoolean("muxi.disableDimensionShaderSwap")&&exists("net.irisshaders.iris.Iris")&&exists("com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager")&&!exists("com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh")&&exists(target);}
    public void acceptTargets(Set<String> own,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
