package net.muxigame.core.feature.identity;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Small platform-independent rules shared by runtime and local Java tests. */
public final class IdentityRules {
    private IdentityRules() {}

    public static boolean validUid(String name) {
        return name != null && name.matches("[1-9][0-9]{4,15}");
    }
    public static UUID offlineUuid(String uid) {
        if (!validUid(uid)) throw new IllegalArgumentException("Invalid UID");
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + uid).getBytes(StandardCharsets.UTF_8));
    }
    public static boolean validDisplayName(String display) {
        return display != null && !display.isBlank() && display.equals(display.strip())
            && display.codePointCount(0, display.length()) <= 64
            && display.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == 0xA7);
    }
}
