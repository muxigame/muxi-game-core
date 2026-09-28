package net.muxigame.core;
import net.muxigame.core.feature.challenge.*;
import java.util.*;
public final class ChallengeRulesSelfTest {
    private static int passed;
    private static void check(String name,boolean pass){if(!pass)throw new AssertionError(name);passed++;}
    public static int run(){
        check("extreme has twenty five waves",ChallengeRules.Difficulty.EXTREME.waves==25);
        check("ammo cooldown ten seconds",ChallengeRules.AMMO_COOLDOWN==200);
        check("coin production reduced fivefold",ChallengeRules.EXCHANGE_RATE==500);
        for(int wave=1;wave<=25;wave++){
            check("no solo batch discount "+wave,ChallengeRules.batchSize(wave,1)==ChallengeRules.batchSize(wave,4));
            int[] floors=new int[3];for(int i=0;i<100;i++)floors[ChallengeRules.spawnFloor(wave,i)]++;
            check("floor one always majority "+wave,floors[0]>=75);
            check("upper floor unlock schedule "+wave,(wave>=8||floors[1]==0)&&(wave>=15||floors[2]==0));
            var bosses=ChallengeRules.bosses(wave,ChallengeRules.Difficulty.EXTREME);
            check("extreme boss composition "+wave,bosses.isEmpty()||bosses.size()==3&&bosses.getFirst()!=ChallengeRules.Enemy.IRON&&bosses.get(1)==ChallengeRules.Enemy.IRON&&bosses.get(2)==ChallengeRules.Enemy.IRON);
            for(int i=0;i<120;i++){var kind=ChallengeRules.enemy(wave,ChallengeRules.Difficulty.EXTREME,i);check("ordinary zombies never accelerated "+wave+":"+i,kind!=ChallengeRules.Enemy.ZOMBIE||kind.speed==0.12);}
        }
        check("distinct upper floor plans",!ResearchLayout.rooms(1).equals(ResearchLayout.rooms(2)));
        check("core nest exists only at the bottom",ResearchLayout.rooms(0).stream().anyMatch(r->r.kind().equals("nest"))&&ResearchLayout.rooms(1).stream().noneMatch(r->r.kind().equals("nest")));
        return passed;
    }
}
