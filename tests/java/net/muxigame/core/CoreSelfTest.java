package net.muxigame.core;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.core.feature.spawning.SpawnCategoryRules;
import net.muxigame.core.client.tasks.TaskHudSettingsSelfTest;

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
        passed += OpSyncSelfTest.run();
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

        // ---- 进服核验（login）----
        check("login off by default", !CoreConfig.parse("{}").login().enabled());
        check("identity alone leaves login off", !CoreConfig.parse(enabled).login().enabled());
        String join = "{\"schema\":1,\"features\":{\"login\":{\"enabled\":true,\"endpoint\":\"https://example.com/join/\",\"serverKey\":\"" + key + "\",\"timeoutSeconds\":6}}}";
        check("login parse", CoreConfig.parse(join).login().enabled());
        // 两节独立：只配 login 时 identity 关着，但 login 必须真的生效。
        // 拆分之前这里是"没有 identity 就整份配置作废"，加功能时最容易踩的就是这条。
        check("login alone leaves identity off", !CoreConfig.parse(join).identity().enabled());
        check("login redacted toString", !CoreConfig.parse(join).toString().contains(key));
        check("login timeout default", CoreConfig.parse(join.replace(",\"timeoutSeconds\":6", "")).login().timeoutSeconds() == 6);
        invalid(join.replace("https://example.com", "http://example.com"));
        invalid(join.replace(key, "short"));
        invalid(join.replace("\"timeoutSeconds\":6", "\"timeoutSeconds\":0"));
        invalid(join.replace("\"timeoutSeconds\":6", "\"timeoutSeconds\":21"));
        invalid(join.replace("\"timeoutSeconds\":6", "\"timeoutSeconds\":\"6\""));
        invalid(join.replace("\"timeoutSeconds\":6", "\"timeoutSeconds\":6.5"));
        // 两节同时开是生产上的形态。
        String both = "{\"schema\":1,\"features\":{\"identity\":{\"enabled\":true,\"endpoint\":\"https://example.com/profile/\",\"serverKey\":\"" + key + "\"},\"login\":{\"enabled\":true,\"endpoint\":\"https://example.com/join/\",\"serverKey\":\"" + key + "\"}}}";
        check("both features together", CoreConfig.parse(both).identity().enabled() && CoreConfig.parse(both).login().enabled());
        // 刷怪类别过滤：刷不出东西的跳过，MONSTER（事件加妖精）和 MISC 永远不动，暮色森林整个不碰。
        boolean[] skip = SpawnCategoryRules.toSkip(new boolean[] {false, true, false, false, false}, 0, 4);
        check("monster kept even if empty", !skip[0]);
        check("possible category kept", !skip[1]);
        check("impossible categories skipped", skip[2] && skip[3]);
        check("misc untouched", !skip[4]);
        check("twilight forest left alone", SpawnCategoryRules.leftAlone("twilightforest"));
        check("nether filtered", !SpawnCategoryRules.leftAlone("minecraft"));
        // 客户端兼容各块的纯逻辑测试各自一个类，互不干扰。
        passed += MapsSelfTest.run() + CommandsSelfTest.run() + DisplaysSelfTest.run() + TasksSelfTest.run()
            + MainlineTasksSelfTest.run() + TaskHudSettingsSelfTest.run();
        System.out.println("Game Core Java self-tests: " + passed + " passed");
    }
}
