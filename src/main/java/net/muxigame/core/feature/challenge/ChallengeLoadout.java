package net.muxigame.core.feature.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;

/** A server-side slot and component snapshot prevents swapping an item after selecting it. */
public record ChallengeLoadout(int primary,int secondary,ItemStack first,ItemStack second) {
    public static ChallengeLoadout defaults(){return new ChallengeLoadout(-1,-1,ItemStack.EMPTY,ItemStack.EMPTY);}
    public static boolean weapon(ItemStack s){return !s.isEmpty() && ModList.get().isLoaded("tacz") && ChallengeGuns.isGun(s);}
    public ChallengeLoadout select(ServerPlayer p,boolean main,int slot){
        if(slot< -1 || slot>=36)throw new IllegalArgumentException("请选择背包中的武器");
        if(slot>=0 && slot==(main?secondary:primary))throw new IllegalArgumentException("主副武器不能选择同一个背包槽位");
        ItemStack stack=slot<0?ItemStack.EMPTY:p.getInventory().getItem(slot);
        if(slot>=0 && !weapon(stack))throw new IllegalArgumentException("该槽位不是可用武器");
        return main?new ChallengeLoadout(slot,secondary,stack.copy(),second):new ChallengeLoadout(primary,slot,first,stack.copy());
    }
    public void validate(ServerPlayer p){
        if(primary>=0 && !ItemStack.matches(first,p.getInventory().getItem(primary)) || secondary>=0 && !ItemStack.matches(second,p.getInventory().getItem(secondary)))
            throw new IllegalArgumentException("所选武器或改装已变化，请重新选择武器再开局");
    }
}
