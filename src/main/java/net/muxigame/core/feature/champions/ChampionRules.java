package net.muxigame.core.feature.champions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;

/** 谁能当强敌。原版 Champions 只挑敌对生物，Champions Unofficial 21.1 把条件放宽成了"任何 Mob"。 */
public final class ChampionRules {
    private ChampionRules() {}

    /**
     * 实现了 Enemy，或者按怪物类别刷新的。后一条兜住那些没实现 Enemy 的模组怪；
     * 末影人、僵尸猪灵这类"惹了才打"的怪物也算在内，动物、鱼、村民、蝙蝠、宠物不算。
     */
    public static boolean mayBeChampion(LivingEntity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }
}
