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
    public static final List<Offer> OFFERS=List.of(
        new Offer("emerald","绿宝石 ×4",200,"minecraft:emerald",4,""),
        new Offer("powder","火药 ×32",250,"minecraft:gunpowder",32,""),
        new Offer("brass","黄铜 ×16",300,"create:brass_ingot",16,""),
        new Offer("diamond","钻石 ×2",400,"minecraft:diamond",2,""),
        new Offer("apple","附魔金苹果 ×1",1500,"minecraft:enchanted_golden_apple",1,""),
        new Offer("netherite","下界合金碎片 ×1",1800,"minecraft:netherite_scrap",1,""),
        new Offer("glock","格洛克 17",2500,"",1,"tacz:glock_17"),
        new Offer("ak47","AK47",6000,"",1,"tacz:ak47"));
    public static int taskCredits(int task){return switch(task){case 0->350;case 1->600;case 2->1200;default->0;};}
}
