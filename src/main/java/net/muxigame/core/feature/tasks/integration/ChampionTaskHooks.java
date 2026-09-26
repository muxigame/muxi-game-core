package net.muxigame.core.feature.tasks.integration;

import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.champions.api.ChampionsApi;

/** Loaded only when Champions is installed. No localized-name or particle guessing. */
public final class ChampionTaskHooks {
    private ChampionTaskHooks() {}
    public static int tier(LivingEntity entity) {
        return ChampionsApi.get().getChampion(entity).map(c->c.tier().level()).orElse(0);
    }
}
