package net.muxigame.transferprobe;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class ProbePlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("muxi.transferProbe")) return false;
        if (target.startsWith("net.irisshaders.") || target.startsWith("com.euphoriapatches.") || target.startsWith("net.caffeinemc.")) {
            try {
                return MixinService.getService().getBytecodeProvider().getClassNode(target) != null;
            } catch (Exception unavailable) {
                return false;
            }
        }
        return true;
    }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
