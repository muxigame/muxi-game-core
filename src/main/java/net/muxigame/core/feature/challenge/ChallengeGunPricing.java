package net.muxigame.core.feature.challenge;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.*;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Price from current smithing materials, with 25% assembly premium. Unknown inputs are never free. */
public final class ChallengeGunPricing {
    private ChallengeGunPricing(){}
    public static final Map<String,Integer> UNIT=Map.ofEntries(
        Map.entry("c:ingots/iron",200),Map.entry("c:ingots/gold",900),Map.entry("c:ingots/copper",100),
        Map.entry("c:ingots/brass",600),Map.entry("c:gems/lapis",300),Map.entry("c:gems/diamond",4000),
        Map.entry("c:gems/emerald",1200),Map.entry("c:gems/quartz",200),Map.entry("c:gems/amethyst",400),
        Map.entry("c:rods/blaze",1200),Map.entry("c:ingots/netherite",99600),Map.entry("minecraft:logs",50));
    private static final Map<String,Integer> ITEMS=Map.ofEntries(
        Map.entry("minecraft:iron_ingot",200),Map.entry("minecraft:gold_ingot",900),Map.entry("minecraft:copper_ingot",100),
        Map.entry("create:brass_ingot",600),Map.entry("minecraft:lapis_lazuli",300),Map.entry("minecraft:diamond",4000),
        Map.entry("minecraft:emerald",1200),Map.entry("minecraft:quartz",200),Map.entry("minecraft:amethyst_shard",400),
        Map.entry("minecraft:blaze_rod",1200),Map.entry("minecraft:netherite_ingot",99600),Map.entry("minecraft:netherite_scrap",24000));
    public static int value(ItemStack stack){
        Integer fixed=ITEMS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());if(fixed!=null)return fixed;
        return UNIT.entrySet().stream().filter(e->stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(e.getKey())))).mapToInt(Map.Entry::getValue).min().orElse(-1);
    }
    public static int price(GunSmithTableRecipe recipe){
        if(recipe.getInputs().isEmpty())return -1;
        long materials=0;
        for(var input:recipe.getInputs()){
            int unit=Arrays.stream(input.getIngredient().getItems()).mapToInt(ChallengeGunPricing::value).filter(n->n>0).min().orElse(-1);
            if(unit<0||input.getCount()<=0)return -1;materials+=(long)unit*input.getCount();
        }
        int count=Math.max(1,recipe.getOutput().getCount());long marked=(materials*5+4L*count-1)/(4L*count);
        return (int)Math.min(100000000,((marked+99)/100)*100);
    }
    private static boolean makes(GunSmithTableRecipe recipe,String gunId){
        ItemStack output=recipe.getOutput();if(output==null||output.isEmpty())return false;IGun gun=IGun.getIGunOrNull(output);
        return gun!=null&&gun.getGunId(output).toString().equals(gunId);
    }
    public static int price(ServerPlayer p,String gunId){
        List<GunSmithTableRecipe> actual=p.server.getRecipeManager().getRecipes().stream().map(h->h.value()).filter(r->r instanceof GunSmithTableRecipe).map(r->(GunSmithTableRecipe)r).filter(r->makes(r,gunId)).toList();
        var recipes=actual.isEmpty()?TimelessAPI.getAllRecipes().values().stream().filter(r->makes(r,gunId)).toList():actual;
        return recipes.stream().mapToInt(ChallengeGunPricing::price).filter(n->n>0).min().orElse(-1);
    }
}
