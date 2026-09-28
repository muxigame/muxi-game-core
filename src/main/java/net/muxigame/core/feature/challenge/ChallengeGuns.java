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
        return gun(p.registryAccess(),id);
    }
    public static ItemStack gun(net.minecraft.core.HolderLookup.Provider registries,String id) {
        var key=ResourceLocation.parse(id);
        if(TimelessAPI.getCommonGunIndex(key).isEmpty()) return ItemStack.EMPTY;
        var modes=TimelessAPI.getCommonGunIndex(key).orElseThrow().getGunData().getFireModeSet();
        return GunItemBuilder.create().setId(key).setCount(1).setAmmoCount(0).setAmmoInBarrel(false)
            .setFireMode(modes.contains(FireMode.AUTO)?FireMode.AUTO:modes.getFirst()).build(registries);
    }
    public static String gunId(ItemStack stack){var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);return gun==null?"":gun.getGunId(stack).toString();}
    public static String type(ItemStack stack){return TimelessAPI.getCommonGunIndex(ResourceLocation.parse(gunId(stack))).map(i->i.getType().toLowerCase(java.util.Locale.ROOT)).orElse("");}
    public static boolean pistol(ItemStack stack){return isGun(stack)&&type(stack).equals("pistol");}
    public static boolean launcher(ItemStack stack){return isGun(stack)&&(type(stack).equals("rpg")||type(stack).equals("launcher"));}
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
        for(int slot=0;slot<3;slot++){String id=ammoId(p.getInventory().getItem(slot));if(!id.isEmpty())ids.add(id);}
        return ids.stream().map(id->TaczTaskHooks.ammo(id,count)).toList();
    }
    public static int capacity(ItemStack stack){
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun==null)return 0;
        return TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(i->com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(stack,i.getGunData())).orElse(0);
    }
    public static void refill(ServerPlayer p){
        com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p).cancelReload();
        var reserve=new java.util.LinkedHashMap<String,Integer>();
        var rockets=new java.util.HashSet<String>();
        for(int slot=0;slot<3;slot++){
            ItemStack stack=p.getInventory().getItem(slot);var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun==null)continue;
            int capacity=capacity(stack);if(capacity<=0)continue;
            gun.setCurrentAmmoCount(stack,capacity);
            var data=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow().getGunData();
            gun.setBulletInBarrel(stack,data.getBolt()!=com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT);
            reserve.merge(data.getAmmoId().toString(),capacity*4,Integer::sum);
            if(launcher(stack))rockets.add(data.getAmmoId().toString());
        }
        for(int i=3;i<36;i++){
            ItemStack s=p.getInventory().getItem(i);var ammo=com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(s);
            if(ammo!=null && reserve.containsKey(ammo.getAmmoId(s).toString()))p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        for(var entry:reserve.entrySet()){
            int remaining=rockets.contains(entry.getKey())?Math.max(4,Math.min(12,entry.getValue())):Math.max(120,Math.min(600,entry.getValue()));
            while(remaining>0){int n=Math.min(60,remaining);ItemStack stack=TaczTaskHooks.ammo(entry.getKey(),n);for(int slot=3;slot<36&&!stack.isEmpty();slot++)if(p.getInventory().getItem(slot).isEmpty()){p.getInventory().setItem(slot,stack);stack=ItemStack.EMPTY;}remaining-=n;}
        }
        p.inventoryMenu.broadcastChanges();
    }
    public static void fillMagazine(ItemStack stack){var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun==null)return;gun.setCurrentAmmoCount(stack,capacity(stack));var data=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow().getGunData();gun.setBulletInBarrel(stack,data.getBolt()!=com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT);}
}
