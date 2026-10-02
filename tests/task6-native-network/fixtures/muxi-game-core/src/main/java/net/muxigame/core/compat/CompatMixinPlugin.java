package net.muxigame.core.compat;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 兼容 mixin 按"目标模组装了才套"。mixin 放在 {@code compat.mixin.<键>.*} 下，键对应下面的模组 id；
 * {@code minecraft} 键是原版，总是套。玩家删了某个模组时，不加载对应的显示或运行时兼容补丁。
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/compat");
    private static final String PREFIX = "net.muxigame.core.compat.mixin.";
    private static final Map<String, String> REQUIRED_MOD = Map.ofEntries(
        Map.entry("minecraft", "minecraft"),
        Map.entry("customskinloader", "customskinloader"),
        Map.entry("twilightforest", "twilightforest"),
        Map.entry("blueprint", "blueprint"),
        Map.entry("mowziesmobs", "mowziesmobs"),
        Map.entry("sereneseasons", "sereneseasons"),
        Map.entry("pasterdream", "pasterdream"),
        Map.entry("xaerominimap", "xaerominimap"),
        Map.entry("xaeroworldmap", "xaeroworldmap"),
        Map.entry("waystones", "waystones"),
        Map.entry("jade", "jade"),
        Map.entry("yigd", "yigd"),
        Map.entry("ftbteams", "ftbteams"),
        Map.entry("watut", "watut"),
        Map.entry("pingwheel", "pingwheel"),
        Map.entry("leaderboards", "leaderboards"),
        Map.entry("refinedstorage", "refinedstorage"),
        Map.entry("customnpcs", "customnpcs"),
        Map.entry("goblintraders", "goblintraders"),
        Map.entry("openpac", "openpartiesandclaims"));

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(PREFIX)) return true;
        String rest = mixinClassName.substring(PREFIX.length());
        int dot = rest.indexOf('.');
        String key = dot < 0 ? "" : rest.substring(0, dot);
        String modId = REQUIRED_MOD.get(key);
        if (modId == null) {
            // 新加 mixin 忘了登记键：宁可不套也别猜，日志里一眼能看到。
            LOG.warn("Compat mixin {} is in an unregistered package '{}'; skipped", mixinClassName, key);
            return false;
        }
        boolean present = "minecraft".equals(modId) || LoadingModList.get().getModFileById(modId) != null;
        if (!present) LOG.info("Compat mixin {} skipped: mod {} is not installed", mixinClassName, modId);
        return present;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
