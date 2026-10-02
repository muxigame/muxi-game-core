package net.muxigame.core.feature.spawning;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.ServerFeature;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import java.util.Set;

/** Stop ambient population in the original home dimension, before entities enter the world. */
public final class HomeSpawningFeature implements ServerFeature {
    // EVENT is also used by mods for deliberate summons. Only audited ambient event populations belong here.
    private static final Set<String> AMBIENT_EVENTS = Set.of(
        "minecraft:wandering_trader", "minecraft:trader_llama", "minecraft:zombie",
        "goblintraders:goblin_trader", "goblintraders:vein_goblin_trader");

    public static boolean home(Level level) { return level.dimension().equals(Level.OVERWORLD); }

    public static boolean blocked(Level level, MobSpawnType reason, EntityType<?> type) {
        if (!home(level)) return false;
        return switch (reason) {
            case NATURAL, CHUNK_GENERATION, PATROL, REINFORCEMENT -> true;
            case EVENT -> AMBIENT_EVENTS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            default -> false;
        };
    }

    @Override public String id() { return "home-spawning"; }
    @Override public void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, this::phantoms);
        bus.addListener(EventPriority.LOWEST, this::placement);
        bus.addListener(EventPriority.LOWEST, this::position);
        bus.addListener(EventPriority.LOWEST, true, this::finalizeSpawn);
    }

    private void phantoms(PlayerSpawnPhantomsEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && home(level))
            event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
    }

    private void placement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (blocked(event.getLevel().getLevel(), event.getSpawnType(), event.getEntityType()))
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    private void position(MobSpawnEvent.PositionCheck event) {
        if (blocked(event.getLevel().getLevel(), event.getSpawnType(), event.getEntity().getType()))
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    private void finalizeSpawn(FinalizeSpawnEvent event) {
        if (blocked(event.getLevel().getLevel(), event.getSpawnType(), event.getEntity().getType())) {
            // setCanceled alone only skips initialization; it does NOT prevent addFreshEntity.
            event.setSpawnCancelled(true);
            event.setCanceled(true);
        }
    }

    @Override public void close() {}
}
