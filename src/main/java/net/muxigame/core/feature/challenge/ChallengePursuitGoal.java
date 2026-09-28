package net.muxigame.core.feature.challenge;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import java.util.EnumSet;

/** Arena zombies have no random strolling/retaliation goals: move, look and attack the assigned player. */
public final class ChallengePursuitGoal extends Goal {
    private final Zombie zombie;
    private int nextPath,nextAttack;
    public ChallengePursuitGoal(Zombie zombie){this.zombie=zombie;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public boolean canUse(){return zombie.getTarget()!=null && zombie.getTarget().isAlive();}
    public boolean canContinueToUse(){return canUse();}
    public boolean requiresUpdateEveryTick(){return true;}
    public void start(){zombie.setAggressive(true);nextPath=0;}
    public void stop(){zombie.getNavigation().stop();zombie.setAggressive(false);}
    public void tick(){
        var target=zombie.getTarget();if(target==null)return;
        zombie.getLookControl().setLookAt(target,30,30);
        if(--nextPath<=0){nextPath=10;zombie.getNavigation().moveTo(target,1.15);}
        if(nextAttack>0)nextAttack--;
        if(nextAttack==0 && zombie.distanceToSqr(target)<=4 && zombie.hasLineOfSight(target)){
            zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);zombie.doHurtTarget(target);nextAttack=20;
        }
    }
}
