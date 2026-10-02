package net.muxigame.core.compat.shaders;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import org.objectweb.asm.tree.ClassNode;
import java.util.*;
/** Optional and client-only; do not compete with newer Euphoria's native lean refresh. */
public final class DimensionShaderPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg){}
    public String getRefMapperConfig(){return null;}
    private boolean exists(String name){try{return MixinService.getService().getBytecodeProvider().getClassNode(name)!=null;}catch(Exception e){return false;}}
    public boolean shouldApplyMixin(String target,String mixin){return !Boolean.getBoolean("muxi.disableDimensionShaderSwap")&&exists("net.irisshaders.iris.Iris")&&exists("com.euphoriapatches.euphoria_patcher.integration.iris.IrisReloadManager")&&!exists("com.euphoriapatches.euphoria_patcher.integration.iris.DimensionShaderRefresh")&&exists(target);}
    public void acceptTargets(Set<String> own,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
