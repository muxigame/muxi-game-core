package net.muxigame.core.feature.challenge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.muxigame.core.feature.tasks.integration.TaczTaskHooks;

/** Only loaded after the optional TaCZ presence check. Native builders preserve functional components. */
public final class ChallengeGuns {
    private ChallengeGuns() {}
    public static ItemStack gun(ServerPlayer p,String id) {
        var key=ResourceLocation.parse(id);
        if(TimelessAPI.getCommonGunIndex(key).isEmpty()) return ItemStack.EMPTY;
        return GunItemBuilder.create().setId(key).setCount(1).setAmmoCount(0).setAmmoInBarrel(false)
            .setFireMode(FireMode.SEMI).build(p.registryAccess());
    }
    public static ItemStack ammo(int count) {return TaczTaskHooks.ammo("tacz:9mm",count);}
}
