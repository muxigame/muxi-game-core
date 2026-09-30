package net.muxigame.core.feature.rules;

import net.minecraft.world.entity.monster.Creeper;
import net.muxigame.core.feature.ServerFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Keeps creeper damage/knockback while preventing their explosions from changing terrain. */
public final class CreeperTerrainProtection implements ServerFeature {
    @Override public String id() { return "creeper-terrain-protection"; }
    @Override public void register(IEventBus gameBus) { gameBus.addListener(this::onExplosion); }
    private void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof Creeper) event.getAffectedBlocks().clear();
    }
    @Override public void close() {}
}
