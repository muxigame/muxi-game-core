package net.muxigame.shadernative;
import org.spongepowered.asm.mixin.extensibility.*;import org.objectweb.asm.tree.ClassNode;import java.util.*;
public final class QAMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;} public boolean shouldApplyMixin(String t,String m){return FilesQA.enabled();} public void acceptTargets(Set<String>a,Set<String>b){} public List<String> getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
