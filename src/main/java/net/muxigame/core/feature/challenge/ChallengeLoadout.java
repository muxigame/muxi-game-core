package net.muxigame.core.feature.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;

/** A server-side slot and component snapshot prevents swapping an item after selecting it. */
public record ChallengeLoadout(int primary,int secondary,ItemStack first,ItemStack second,int primary2,ItemStack third) {
    public static ChallengeLoadout defaults(){return new ChallengeLoadout(-1,-1,ItemStack.EMPTY,ItemStack.EMPTY,-1,ItemStack.EMPTY);}
    public static boolean weapon(ItemStack s){return !s.isEmpty() && ModList.get().isLoaded("tacz") && ChallengeGuns.isGun(s);}
    public ChallengeLoadout select(ServerPlayer p,boolean main,int slot){
        return select(p,main?0:2,slot);
    }
    public ChallengeLoadout select(ServerPlayer p,int role,int slot){
        if(role<0||role>2)throw new IllegalArgumentException("无效枪槽");
        if(slot< -1 || slot>=36)throw new IllegalArgumentException("请选择背包中的武器");
        if(slot>=0&&(role!=0&&slot==primary||role!=1&&slot==primary2||role!=2&&slot==secondary))throw new IllegalArgumentException("三个武器槽不能选择同一个背包槽位");
        ItemStack stack=slot<0?ItemStack.EMPTY:p.getInventory().getItem(slot);
        if(slot>=0 && !weapon(stack))throw new IllegalArgumentException("该槽位不是可用武器");
        if(slot>=0&&(role==2)!=ChallengeGuns.pistol(stack))throw new IllegalArgumentException(role==2?"副武器只可选手枪":"主武器请选择非手枪枪械");
        return switch(role){case 0->new ChallengeLoadout(slot,secondary,stack.copy(),second,primary2,third);case 1->new ChallengeLoadout(primary,secondary,first,second,slot,stack.copy());default->new ChallengeLoadout(primary,slot,first,stack.copy(),primary2,third);};
    }
    public void validate(ServerPlayer p){
        if(primary>=0 && !ItemStack.matches(first,p.getInventory().getItem(primary)) || secondary>=0 && !ItemStack.matches(second,p.getInventory().getItem(secondary))||primary2>=0&&!ItemStack.matches(third,p.getInventory().getItem(primary2)))
            throw new IllegalArgumentException("所选武器或改装已变化，请重新选择武器再开局");
    }
    public int fee(ServerPlayer p){int fee=0;for(var stack:java.util.List.of(first,third,second))if(!stack.isEmpty())fee+=ChallengeBattleShop.carryFee(p,stack);return fee;}
}
