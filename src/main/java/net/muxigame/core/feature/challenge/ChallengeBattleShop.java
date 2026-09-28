package net.muxigame.core.feature.challenge;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;

/** Temporary round equipment; never exported to the permanent inventory. All prices are server derived. */
public final class ChallengeBattleShop {
    private ChallengeBattleShop(){}
    public record Offer(String id,String title,String category,String gun,Item item,String potion,int cost,int coins){
        public ItemStack stack(ServerPlayer p){return !gun.isEmpty()?ChallengeGuns.gun(p,gun):!potion.isEmpty()?ChallengeBattleShop.potion(potion):new ItemStack(item);}
    }
    private static final String[][] GUNS={
        {"glock_17","格洛克17","手枪"},{"deagle","沙漠之鹰","手枪"},{"colt_1911","M1911","手枪"},{"cz75","CZ75","手枪"},
        {"hk_mp5a5","MP5A5","冲锋枪"},{"p90","P90","冲锋枪"},{"ump45","UMP45","冲锋枪"},
        {"ak47","AK47","步枪"},{"m4a1","M4A1","步枪"},{"aug","AUG","步枪"},{"hk416d","HK416D","步枪"},{"scar_h","SCAR-H","步枪"},
        {"m249","M249","机枪"},{"rpk","RPK","机枪"},{"kar98","Kar98k","狙击枪"},{"m700","M700","狙击枪"},{"ai_awp","AWP","狙击枪"},
        {"m870","M870","霰弹枪"},{"m1014","M1014","霰弹枪"},{"rpg7","RPG-7","爆炸物"}};
    public static List<Offer> offers(ServerPlayer p){
        List<Offer> result=new ArrayList<>();
        for(var gun:GUNS){String id="tacz:"+gun[0];var stack=ChallengeGuns.gun(p,id);if(stack.isEmpty())continue;int price=ChallengeGunPower.estimate(stack).price();result.add(new Offer(gun[0],gun[1],gun[2],id,Items.AIR,"",price,0));}
        result.add(new Offer("iron_armor","铁甲套装","护甲","",Items.IRON_CHESTPLATE,"",500,0));
        result.add(new Offer("diamond_armor","钻石甲套装","护甲","",Items.DIAMOND_CHESTPLATE,"",1600,0));
        result.add(new Offer("netherite_armor","下界合金甲套装","护甲","",Items.NETHERITE_CHESTPLATE,"",3200,0));
        result.add(new Offer("heal","治疗药剂II","补给","",Items.POTION,"heal",250,2));
        result.add(new Offer("jump","跳跃药剂","补给","",Items.POTION,"jump",180,3));
        result.add(new Offer("speed","迅捷药剂","补给","",Items.POTION,"speed",180,3));
        return List.copyOf(result);
    }
    public static ItemStack potion(String kind){return PotionContents.createItemStack(Items.POTION,switch(kind){case "heal"->Potions.STRONG_HEALING;case "jump"->Potions.LEAPING;case "speed"->Potions.SWIFTNESS;default->throw new IllegalArgumentException("未知药剂");});}
    public static int carryFee(ServerPlayer p,ItemStack stack){return ChallengeGunPower.estimate(stack).fee();}
}
