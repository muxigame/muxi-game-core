package net.muxigame.core.feature.dimensions;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.MinecraftServer;

/** Persistent paired gates, shared by players and stored in the unchanged home world. */
public final class PortalLinks extends SavedData {
    private final CompoundTag links;
    public PortalLinks() { this(new CompoundTag()); }
    private PortalLinks(CompoundTag links) { this.links=links; }
    public static PortalLinks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PortalLinks::new,(tag,registries)->new PortalLinks(tag.getCompound("links")),null),"muxi_world_portals");
    }
    public CompoundTag endpoint(String key) { return links.getCompound(key); }
    public void pair(String first,CompoundTag firstEndpoint,String second,CompoundTag secondEndpoint) {
        links.put(first,secondEndpoint);links.put(second,firstEndpoint);setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) { tag.put("links",links);return tag; }
}
