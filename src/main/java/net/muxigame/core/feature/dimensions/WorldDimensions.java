package net.muxigame.core.feature.dimensions;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.Set;

/** The old overworld remains the home save. Labels must never be used as storage keys. */
public final class WorldDimensions {
    public record Destination(String command, ResourceKey<Level> key, String name, boolean resettable) {}
    public static final ResourceKey<Level> OVERWORLD = key("overworld");
    public static final ResourceKey<Level> ADVENTURE = key("adventure");
    public static final Set<ResourceKey<Level>> EXPLORATION = Set.of(OVERWORLD, ADVENTURE);
    public static final List<Destination> ALL = List.of(
        new Destination("home", Level.OVERWORLD, "家园", false),
        new Destination("overworld", OVERWORLD, "生存世界", true),
        new Destination("adventure", ADVENTURE, "冒险世界", false));
    private WorldDimensions() {}
    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("muxi_game_core", path));
    }
    public static String name(ResourceKey<Level> key) {
        return ALL.stream().filter(d -> d.key().equals(key)).map(Destination::name).findFirst().orElse(key.location().toString());
    }
    public static boolean managed(ResourceKey<Level> key) {
        return ALL.stream().anyMatch(d -> d.key().equals(key));
    }
    public static boolean exploration(ResourceKey<Level> key) {
        return EXPLORATION.contains(key);
    }
    public static boolean exploration(ResourceLocation location) {
        return EXPLORATION.stream().anyMatch(key -> key.location().equals(location));
    }
    public static boolean resettable(ResourceKey<Level> key) {
        return ALL.stream().filter(d -> d.key().equals(key)).findFirst().map(Destination::resettable).orElse(false);
    }
}
