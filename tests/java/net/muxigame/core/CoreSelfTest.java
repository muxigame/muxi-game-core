package net.muxigame.core;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;

public final class CoreSelfTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
    private static void invalid(String json) {
        try { CoreConfig.parse(json); throw new AssertionError("Expected config rejection"); }
        catch (IllegalArgumentException error) { check("no secret in errors", !error.getMessage().contains("test-secret")); }
    }
    public static void main(String[] args) {
        check("UID numeric", IdentityRules.validUid("10000"));
        check("reject username", !IdentityRules.validUid("Roc"));
        check("reject leading zero", !IdentityRules.validUid("010000"));
        check("reject too short", !IdentityRules.validUid("9999"));
        check("reject too long", !IdentityRules.validUid("10000000000000000"));
        check("Java UUID matches launcher", IdentityRules.offlineUuid("10000").toString().equals("bac84aa3-61d5-3a42-acb0-02ab44e17ee2"));
        check("distinct identities", !IdentityRules.offlineUuid("10000").equals(IdentityRules.offlineUuid("10001")));
        check("Chinese nickname", IdentityRules.validDisplayName("洛可"));
        check("spaces", IdentityRules.validDisplayName("洛可 test"));
        check("no blank", !IdentityRules.validDisplayName("  "));
        check("no formatting", !IdentityRules.validDisplayName("§a洛可"));
        check("no newlines", !IdentityRules.validDisplayName("洛可\n"));
        check("no bidi", !IdentityRules.validDisplayName("洛可\u202e"));
        check("length cap", !IdentityRules.validDisplayName("洛".repeat(65)));
        check("disabled default", !CoreConfig.parse("{}").identity().enabled());
        check("no credentials needed when disabled", !CoreConfig.parse("{\"features\":{\"identity\":{\"enabled\":false}}}").identity().enabled());
        String key = "test-secret-".repeat(4);
        String enabled = "{\"schema\":1,\"features\":{\"identity\":{\"enabled\":true,\"endpoint\":\"https://example.com/profile/\",\"serverKey\":\"" + key + "\",\"refreshSeconds\":60}}}";
        check("enabled parse", CoreConfig.parse(enabled).identity().enabled());
        check("redacted toString", !CoreConfig.parse(enabled).toString().contains(key));
        invalid(enabled.replace("https://example.com", "http://example.com"));
        invalid(enabled.replace("profile/", "profile/?private=1/"));
        invalid(enabled.replace("\"schema\":1", "\"schema\":2"));
        invalid(enabled.replace("\"refreshSeconds\":60", "\"refreshSeconds\":0"));
        invalid(enabled.replace("\"refreshSeconds\":60", "\"refreshSeconds\":60.5"));
        invalid(enabled.replace("\"refreshSeconds\":60", "\"refreshSeconds\":\"60\""));
        invalid(enabled.replace(key, "short"));
        check("isolated localhost test allowed", CoreConfig.parse(enabled.replace("https://example.com", "http://127.0.0.1")).identity().enabled());
        System.out.println("Game Core Java self-tests: " + passed + " passed");
    }
}
