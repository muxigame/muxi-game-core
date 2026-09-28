package net.muxigame.core.feature.challenge;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Gameplay estimate, independent of crafting recipes. Not a promise of real-world hit-rate DPS. */
public final class ChallengeGunPower {
    private ChallengeGunPower(){}
    public record Estimate(double shot,double burstDps,double sustainedDps,int magazine,double power,int price,int fee){}
    public static Estimate calculate(double damage,int pellets,double rpm,int capacity,double reload,String type,double explosion,double radius,double penetration,double headshot){
        damage=bounded(damage,1,2000);rpm=bounded(rpm,10,2400);reload=bounded(reload,0.3,20);capacity=Math.max(1,Math.min(300,capacity));
        double shot=damage*Math.max(1,Math.min(20,pellets))+Math.max(0,Math.min(2000,explosion))*Math.min(4,1+Math.max(0,radius)*0.6);
        double burst=shot*rpm/60,sustained=shot*capacity/(capacity*60/rpm+reload);
        double weight=switch(type){case "pistol"->0.8;case "mg","machine_gun"->1.15;case "sniper"->1.2;case "shotgun"->0.85;case "rpg","launcher"->1.3;default->1.0;};
        double power=(burst*0.3+sustained*0.55+Math.sqrt(shot*capacity)*0.8+shot*0.25)*weight*(1+Math.min(1,Math.max(0,penetration))*0.25)*(1+Math.min(4,Math.max(0,headshot-1))*0.1);
        return new Estimate(shot,burst,sustained,capacity,power,Math.max(250,Math.min(16000,(int)Math.ceil(power*8/50)*50)),Math.max(1,Math.min(80,(int)Math.ceil(power/25))));
    }
    private static double bounded(double n,double min,double max){return Double.isFinite(n)?Math.max(min,Math.min(max,n)):min;}
    public static Estimate estimate(ItemStack stack){
        var gun=IGun.getIGunOrNull(stack);if(gun==null)throw new IllegalArgumentException("无效枪械");
        var index=TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow();var data=index.getGunData();var bullet=data.getBulletData();
        double rpm=data.getFireModeSet().stream().mapToInt(data::getRoundsPerMinute).max().orElse(60);
        double bolt=data.getBoltActionTime()+data.getBoltFeedTime();if(bolt>0.05)rpm=Math.min(rpm,60/bolt);
        double reload=data.getReloadData().getFeed().getEmptyTime()+data.getReloadData().getCooldown().getEmptyTime();
        var script=data.getScriptParam();int level=AttachmentDataUtils.getMagExtendLevel(stack,data);
        String prefix=level>0?"empty_xmag_"+level:"empty";
        if(script!=null&&script.get(prefix+"_feed") instanceof Number feed&&script.get(prefix+"_cooldown") instanceof Number cooldown)reload=feed.doubleValue()+cooldown.doubleValue();
        var explosion=bullet.getExplosionData();boolean blast=explosion!=null&&AttachmentDataUtils.isExplodeEnabled(stack,data);
        return calculate(AttachmentDataUtils.getDamageWithAttachment(stack,data),bullet.getBulletAmount(),rpm,ChallengeGuns.capacity(stack),reload,index.getType().toLowerCase(Locale.ROOT),blast?explosion.getDamage():0,blast?explosion.getRadius():0,AttachmentDataUtils.getArmorIgnoreWithAttachment(stack,data),AttachmentDataUtils.getHeadshotMultiplier(stack,data));
    }
}
