package net.muxigame.core.feature.challenge;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Server-authoritative prices and quantities. The client submits only an offer ID and wallet revision. */
public final class ChallengeShop {
    private ChallengeShop(){}
    public record Offer(String id,String title,int cost,String item,int count,String gun) {
        public ItemStack stack(ServerPlayer p){
            if(!gun.isEmpty())return ModList.get().isLoaded("tacz")?ChallengeGuns.gun(p,gun):ItemStack.EMPTY;
            var key=ResourceLocation.parse(item);return BuiltInRegistries.ITEM.containsKey(key)?new ItemStack(BuiltInRegistries.ITEM.get(key),count):ItemStack.EMPTY;
        }
    }
    public static final List<Offer> MATERIALS=List.of(
        new Offer("emerald","绿宝石 ×1",12,"minecraft:emerald",1,""),
        new Offer("powder","火药 ×8",18,"minecraft:gunpowder",8,""),
        new Offer("brass","黄铜 ×4",24,"create:brass_ingot",4,""),
        new Offer("diamond","钻石 ×1",40,"minecraft:diamond",1,""),
        new Offer("apple","附魔金苹果 ×1",180,"minecraft:enchanted_golden_apple",1,""),
        new Offer("netherite","下界合金碎片 ×1",240,"minecraft:netherite_scrap",1,""));
    private static final String[][] GUNS={{"glock","格洛克 17","glock_17"},{"mp5","MP5A5","hk_mp5a5"},{"ak47","AK47","ak47"},{"m4a1","M4A1","m4a1"},{"aug","AUG","aug"},{"m870","M870","m870"},{"m1014","M1014","m1014"},{"kar98","Kar98k","kar98"},{"m700","M700","m700"},{"p90","P90","p90"},{"hk416","HK416D","hk416d"},{"scar_h","SCAR-H","scar_h"},{"m249","M249","m249"},{"awp","AWP","ai_awp"}};
    private static final java.util.Map<net.minecraft.server.MinecraftServer,List<Offer>> CACHE=new java.util.WeakHashMap<>();
    public static synchronized void invalidate(){CACHE.clear();}
    public static synchronized List<Offer> offers(ServerPlayer p){return CACHE.computeIfAbsent(p.server,server->{
        var offers=new java.util.ArrayList<>(MATERIALS);
        if(ModList.get().isLoaded("tacz"))for(String[] gun:GUNS){String id="tacz:"+gun[2];int price=ChallengeGunPricing.price(p,id);if(price>0&&!ChallengeGuns.gun(p,id).isEmpty())offers.add(new Offer(gun[0],gun[1],(price+99)/100,"",1,id));}
        return List.copyOf(offers);
    });}
    public static int taskCredits(int task){return switch(task){case 0->4;case 1->6;case 2->12;default->0;};}
}
