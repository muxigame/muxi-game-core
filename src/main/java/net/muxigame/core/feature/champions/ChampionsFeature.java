package net.muxigame.core.feature.champions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.muxigame.core.feature.ServerFeature;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * 新的强敌由 {@code ChampionSpawnHandlerMixin} 拦在源头；这里处理修复之前已经变成强敌、随区块存盘的
 * 动物和鱼：区块加载时摘掉强敌数据和它加的属性，恢复成普通生物。
 */
public final class ChampionsFeature implements ServerFeature {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/champions");
    private static final ResourceLocation DATA = ResourceLocation.fromNamespaceAndPath("champions", "champion_data");
    private int restored;

    @Override public String id() { return "champions"; }

    @Override public void register(IEventBus gameBus) {
        // 必须抢在 Champions 自己的加载处理前面：它看到强敌数据就会装上词条的 AI 目标，事后没法干净地摘。
        gameBus.addListener(EventPriority.HIGHEST, this::onJoin);
        LOG.info("Champions limited to hostile mobs");
    }

    private void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity entity) || ChampionRules.mayBeChampion(entity)) return;
        AttachmentType<?> type = NeoForgeRegistries.ATTACHMENT_TYPES.get(DATA);
        // 编译用的是未打补丁的原版 jar，Entity 上看不到 NeoForge 加的附加数据方法，运行时它实现了这个接口。
        IAttachmentHolder holder = (IAttachmentHolder) entity;
        if (type == null || !holder.hasData(type)) return;
        holder.removeData(type);
        // 等级和词条加的属性修饰符都在 champions 命名空间下，跟着实体存盘，不摘的话血量和攻击会一直留着。
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attribute -> {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) return;
            List<ResourceLocation> ids = instance.getModifiers().stream().map(AttributeModifier::id)
                .filter(id -> "champions".equals(id.getNamespace())).toList();
            ids.forEach(instance::removeModifier);
        });
        if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        LOG.debug("Restored non-hostile champion {} at {}", entity.getType(), entity.blockPosition());
        if (++restored % 100 == 1) LOG.info("Restored {} non-hostile champion(s) to normal so far", restored);
    }

    @Override public void close() {
        if (restored > 0) LOG.info("Restored {} non-hostile champion(s) to normal this run", restored);
    }
}
