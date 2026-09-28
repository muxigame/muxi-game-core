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
            .setFireMode(id.equals("tacz:hk_mp5a5")?FireMode.AUTO:FireMode.SEMI).build(p.registryAccess());
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
    public static int capacity(ItemStack stack){
        var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun==null)return 0;
        return TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(i->com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(stack,i.getGunData())).orElse(0);
    }
    public static void refill(ServerPlayer p){
        com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p).cancelReload();
        var reserve=new java.util.LinkedHashMap<String,Integer>();
        for(int slot=0;slot<2;slot++){
            ItemStack stack=p.getInventory().getItem(slot);var gun=com.tacz.guns.api.item.IGun.getIGunOrNull(stack);if(gun==null)continue;
            int capacity=capacity(stack);if(capacity<=0)continue;
            gun.setCurrentAmmoCount(stack,capacity);
            var data=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow().getGunData();
            gun.setBulletInBarrel(stack,data.getBolt()!=com.tacz.guns.resource.pojo.data.gun.Bolt.OPEN_BOLT);
            reserve.merge(data.getAmmoId().toString(),capacity*4,Integer::sum);
        }
        for(int i=2;i<36;i++){
            ItemStack s=p.getInventory().getItem(i);var ammo=com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(s);
            if(ammo!=null && reserve.containsKey(ammo.getAmmoId(s).toString()))p.getInventory().setItem(i,ItemStack.EMPTY);
        }
        for(var entry:reserve.entrySet()){
            int remaining=Math.max(120,Math.min(600,entry.getValue()));
            while(remaining>0){int n=Math.min(60,remaining);ItemStack stack=TaczTaskHooks.ammo(entry.getKey(),n);p.getInventory().add(stack);remaining-=n;}
        }
        p.inventoryMenu.broadcastChanges();
    }
}
