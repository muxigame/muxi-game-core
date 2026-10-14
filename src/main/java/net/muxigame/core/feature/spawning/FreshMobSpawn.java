package net.muxigame.core.feature.spawning;
import net.minecraft.world.entity.MobSpawnType;
/** A fresh finalize call, distinct from neoforge:spawn_type restored from old or clone NBT. */
public interface FreshMobSpawn { MobSpawnType muxi$freshSpawnReason(); void muxi$clearFreshSpawnReason(); }
