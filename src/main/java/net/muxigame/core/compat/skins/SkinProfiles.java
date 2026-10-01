package net.muxigame.core.compat.skins;

import com.mojang.authlib.GameProfile;

/** Insecure/skull lookups must not mark a live player's shared profile. */
public final class SkinProfiles {
    private SkinProfiles() {}

    public static GameProfile forInsecureLookup(GameProfile playerProfile) {
        GameProfile copy = new GameProfile(playerProfile.getId(), playerProfile.getName());
        copy.getProperties().putAll(playerProfile.getProperties());
        return copy;
    }
}
