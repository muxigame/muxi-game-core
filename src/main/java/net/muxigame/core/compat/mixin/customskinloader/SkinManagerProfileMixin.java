package net.muxigame.core.compat.mixin.customskinloader;

import com.mojang.authlib.GameProfile;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.muxigame.core.compat.skins.SkinProfiles;
import org.spongepowered.asm.mixin.Mixin;

/** CSL 14.24 marks getInsecureSkin's argument as CSL$IsSkull; YSM passes live profiles. */
@Mixin(value = SkinManager.class, remap = false)
public abstract class SkinManagerProfileMixin {
    // Wrap the complete body, including CSL's HEAD injection, so injection
    // priority cannot cause CSL to mark the live profile before it is copied.
    @WrapMethod(method = "getInsecureSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/resources/PlayerSkin;")
    private PlayerSkin muxi$isolatedSkinProfile(GameProfile profile, Operation<PlayerSkin> original) {
        return original.call(SkinProfiles.forInsecureLookup(profile));
    }
}
