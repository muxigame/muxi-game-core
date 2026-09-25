package net.muxigame.core.feature.spawning;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 自然刷怪时跳过"这个维度里根本刷不出东西"的类别。
 *
 * <p>Aether、Hybrid Aquatic、Friends&amp;Foes 往 MobCategory 里加了 13 个每 tick 都要试的类别（原版 6 个）。
 * 在刷不出东西的维度里这些类别的数量永远是 0、永远到不了上限，于是每个刷怪区块每 tick 都白做一遍群系、结构、
 * 事件查询——实测刷怪占主线程 ~17 ms/tick，下界只有一个玩家也要 ~5 ms。这里按维度算一次哪些类别不可能刷出东西，
 * 刷怪循环直接跳过；能刷出东西的类别一概不动，所以刷怪结果和原版完全一样。
 */
public final class SpawnCategoryFilter {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/spawning");
    /** 按维度记"哪些类别跳过"（下标是 ordinal）。刷怪只在服务端主线程跑，加锁只是防御。 */
    private static final Map<ServerLevel, boolean[]> SKIP = Collections.synchronizedMap(new WeakHashMap<>());

    private SpawnCategoryFilter() {}

    public static boolean impossible(ServerLevel level, MobCategory category) {
        boolean[] skip = SKIP.computeIfAbsent(level, SpawnCategoryFilter::compute);
        int index = category.ordinal();
        return index < skip.length && skip[index];
    }

    private static boolean[] compute(ServerLevel level) {
        MobCategory[] all = MobCategory.values();
        String dimension = level.dimension().location().toString();
        // 暮色森林用 PotentialSpawns 事件往结构里加刷怪，不在群系和结构数据里，算不出来就别碰。
        if (SpawnCategoryRules.leftAlone(level.dimension().location().getNamespace())) {
            LOG.info("Natural spawning in {} left untouched", dimension);
            return new boolean[all.length];
        }
        try {
            boolean[] possible = new boolean[all.length];
            // NeoForge 的 getMobSettings 返回的是套过生物群系修改器之后的结果，模组加的刷怪都在里面。
            for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
                MobSpawnSettings settings = biome.value().getMobSettings();
                for (MobCategory category : all) if (!settings.getMobs(category).isEmpty()) possible[category.ordinal()] = true;
            }
            // 结构的刷怪覆盖（要塞、女巫小屋……）：只看这个维度实际会生成的结构集，写了哪个类别就算能刷。
            for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets())
                for (StructureSet.StructureSelectionEntry entry : set.value().structures())
                    for (MobCategory category : entry.structure().value().getModifiedStructureSettings().spawnOverrides().keySet())
                        possible[category.ordinal()] = true;
            boolean[] skip = SpawnCategoryRules.toSkip(possible, MobCategory.MONSTER.ordinal(), MobCategory.MISC.ordinal());
            List<String> skipped = new ArrayList<>();
            for (MobCategory category : all) if (skip[category.ordinal()]) skipped.add(category.getName());
            LOG.info("Natural spawning in {} skips {} categories nothing can spawn in: {}", dimension, skipped.size(), skipped);
            return skip;
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Could not work out spawnable categories for {}; spawning there left untouched", dimension, error);
            return new boolean[all.length];
        }
    }
}
