package net.muxigame.core.threading;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
public final class ThreadingMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) { return Boolean.getBoolean("muxi.dimensionThreads"); }
    public void acceptTargets(Set<String> own, Set<String> other) {}
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
        // Executor and game threads both inspect the owner during phase handoffs.
        if (mixin.endsWith("LevelOwnerAccess") || mixin.endsWith("ChunkOwnerAccess"))
            node.fields.stream().filter(f -> f.name.equals("thread") || f.name.equals("mainThread"))
                .forEach(f -> f.access = (f.access & ~org.objectweb.asm.Opcodes.ACC_FINAL) | org.objectweb.asm.Opcodes.ACC_VOLATILE);
    }
}
