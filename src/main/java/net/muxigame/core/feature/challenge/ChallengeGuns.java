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
    public static boolean isGun(ItemStack stack){
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        return gun!=null && TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).isPresent();
    }
    public static String ammoId(ItemStack stack){
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        return gun==null?"":TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(i->i.getGunData().getAmmoId().toString()).orElse("");
    }
    public static java.util.List<ItemStack> supplies(ServerPlayer p,int count){
        var ids=new java.util.LinkedHashSet<String>();
        for(int slot=0;slot<2;slot++){String id=ammoId(p.getInventory().getItem(slot));if(!id.isEmpty())ids.add(id);}
        return ids.stream().map(id->TaczTaskHooks.ammo(id,count)).toList();
    }
}
