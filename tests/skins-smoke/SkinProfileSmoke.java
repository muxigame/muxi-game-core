import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import customskinloader.fake.FakeSkinManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.PlayerSkin;
import net.muxigame.core.compat.mixin.customskinloader.SkinManagerProfileMixin;
import net.muxigame.core.compat.skins.SkinProfiles;
import net.muxigame.core.feature.identity.IdentityRules;
import java.util.UUID;

public final class SkinProfileSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static GameProfile player(String uid, String skin) {
        GameProfile p = new GameProfile(IdentityRules.offlineUuid(uid), uid);
        p.getProperties().put("textures", new Property("textures", skin, "fixture-signature"));
        return p;
    }
    public static void main(String[] args) throws Exception {
        GameProfile baseline = player("10001", "friend-a");
        FakeSkinManager.setSkullType(baseline);
        check(baseline.getProperties().containsKey("CSL$IsSkull"), "real CSL must reproduce live-profile pollution");

        GameProfile a = player("10001", "friend-a"), b = player("10002", "friend-b");
        UUID id = a.getId();
        Property texture = a.getProperties().get("textures").iterator().next();
        var wrapper = SkinManagerProfileMixin.class.getDeclaredMethod("muxi$isolatedSkinProfile", GameProfile.class, Operation.class);
        wrapper.setAccessible(true);
        var mixin = new SkinManagerProfileMixin() {};
        // Initial player load -> repeated YSM/skull reads -> later player/profile reads.
        for (int i = 0; i < 120; i++) {
            GameProfile lookup = SkinProfiles.forInsecureLookup(a);
            check(lookup != a && lookup.getProperties() != a.getProperties(), "separate lookup/property map");
            check(lookup.getId().equals(id) && lookup.getName().equals("10001"), "stable identity");
            check(lookup.getProperties().get("textures").iterator().next().equals(texture), "textures and signature retained");
            FakeSkinManager.setSkullType(lookup);
            check(lookup.getProperties().containsKey("CSL$IsSkull"), "CSL still handles insecure lookup");
            check(!a.getProperties().containsKey("CSL$IsSkull"), "subsequent player load must stay a player");
            lookup.getProperties().removeAll("textures");
            check(a.getProperties().get("textures").iterator().next().equals(texture), "lookup changes do not erase player skin");
            Operation<PlayerSkin> original = params -> {
                GameProfile supplied = (GameProfile) params[0];
                check(supplied != a, "production wrapper supplies isolated profile before CSL executes");
                FakeSkinManager.setSkullType(supplied);
                return null;
            };
            check(wrapper.invoke(mixin, a, original) == null, "production wrapper preserves original result");
            check(!a.getProperties().containsKey("CSL$IsSkull"), "production wrapper keeps later player lookup unmarked");
        }
        check(a.getId().equals(id) && a.getName().equals("10001"), "refresh keeps UID/UUID");
        check(b.getProperties().get("textures").iterator().next().value().equals("friend-b"), "second user retains own skin");
        GameProfile noSkin = new GameProfile(IdentityRules.offlineUuid("10003"), "10003");
        check(SkinProfiles.forInsecureLookup(noSkin).getProperties().isEmpty(), "unset skin remains unset");
        System.out.println("PASS: actual CSL pollution reproduced; 120 isolated lookups preserve UID/UUID/textures/signature; two users isolated; unset skin retained.");
    }
}
