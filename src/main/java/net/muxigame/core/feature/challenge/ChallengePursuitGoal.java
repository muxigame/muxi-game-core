package net.muxigame.core.feature.challenge;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import java.util.EnumSet;

/** Arena zombies have no random strolling/retaliation goals: move, look and attack the assigned player. */
public final class ChallengePursuitGoal extends Goal {
    private final Mob zombie;
    private final ChallengeArena arena;
    private int routeFloor=-1;
    private boolean upstairs,atFlight;
    private int nextPath,nextAttack;
    private net.minecraft.core.BlockPos recovery;
    private int recoveryTicks;
    public void recoverTo(net.minecraft.core.BlockPos waypoint){recovery=waypoint;recoveryTicks=80;nextPath=0;}
    public ChallengePursuitGoal(Mob zombie,ChallengeArena arena){this.zombie=zombie;this.arena=arena;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    public boolean canUse(){return zombie.getTarget()!=null && zombie.getTarget().isAlive();}
    public boolean canContinueToUse(){return canUse();}
    public boolean requiresUpdateEveryTick(){return true;}
    public void start(){zombie.setAggressive(true);nextPath=0;}
    public void stop(){zombie.getNavigation().stop();zombie.setAggressive(false);}
    public void tick(){
        var target=zombie.getTarget();if(target==null)return;
        zombie.getLookControl().setLookAt(target,30,30);
        if(recovery!=null && (--recoveryTicks<=0 || recovery.distToCenterSqr(zombie.position())<4))recovery=null;
        if(--nextPath<=0){
            nextPath=10;var waypoint=recovery;
            int current=arena.floor(zombie.getY()),destination=arena.floor(target.getY());
            var currentRoom=arena.roomAt(zombie.getX(),zombie.getY(),zombie.getZ());var targetRoom=arena.roomAt(target.getX(),target.getY(),target.getZ());
            if(waypoint==null&&currentRoom!=null&&currentRoom.interior((int)Math.floor(zombie.getX())-arena.origin(),(int)Math.floor(zombie.getZ()))&&(current!=destination||currentRoom!=targetRoom))waypoint=arena.outside(currentRoom,current);
            if(waypoint==null&&current!=destination){
                boolean up=current<destination;
                if(routeFloor!=current||upstairs!=up){routeFloor=current;upstairs=up;atFlight=false;}
                var entrance=arena.stairEntrance(current,destination);
                var exit=arena.stairExit(current,destination);
                if(entrance.distToCenterSqr(zombie.position())<4)atFlight=true;
                waypoint=atFlight?exit:entrance;
            }else if(current==destination){routeFloor=-1;if(waypoint==null&&targetRoom!=null&&targetRoom!=currentRoom){var entry=arena.outside(targetRoom,current);if(entry.distToCenterSqr(zombie.position())>4)waypoint=entry;}}
            if(waypoint!=null)zombie.getNavigation().moveTo(waypoint.getX()+0.5,waypoint.getY(),waypoint.getZ()+0.5,1.0);
            else zombie.getNavigation().moveTo(target,1.0);
        }
        if(zombie instanceof Creeper creeper){creeper.setSwellDir(zombie.distanceToSqr(target)<=9&&zombie.hasLineOfSight(target)?1:-1);return;}
        if(nextAttack>0)nextAttack--;
        if(nextAttack==0 && zombie.distanceToSqr(target)<=4 && zombie.hasLineOfSight(target)){
            zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);zombie.doHurtTarget(target);nextAttack=20;
        }
    }
}
