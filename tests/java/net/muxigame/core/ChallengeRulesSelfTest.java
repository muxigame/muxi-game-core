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
            check("batch sizes bounded "+wave,ChallengeRules.batchSize(wave,1)>=10&&ChallengeRules.batchSize(wave,4)<=20);
            int[] floors=new int[3];for(int i=0;i<100;i++)floors[ChallengeRules.spawnFloor(wave,i)]++;
            check("floor one always majority "+wave,floors[0]>=75);
            check("upper floor unlock schedule "+wave,(wave>=8||floors[1]==0)&&(wave>=15||floors[2]==0));
            var bosses=ChallengeRules.bosses(wave,ChallengeRules.Difficulty.EXTREME);
            check("extreme boss composition "+wave,bosses.isEmpty()||bosses.size()==3&&bosses.getFirst()!=ChallengeRules.Enemy.IRON&&bosses.get(1)==ChallengeRules.Enemy.IRON&&bosses.get(2)==ChallengeRules.Enemy.IRON);
            for(int i=0;i<120;i++){var kind=ChallengeRules.enemy(wave,ChallengeRules.Difficulty.EXTREME,i);check("ordinary zombies never accelerated "+wave+":"+i,kind!=ChallengeRules.Enemy.ZOMBIE||kind.speed==0.12);}
        }
        check("distinct upper floor plans",!ResearchLayout.rooms(1).equals(ResearchLayout.rooms(2)));
        check("core nest exists only at the bottom",ResearchLayout.rooms(0).stream().anyMatch(r->r.kind().equals("nest"))&&ResearchLayout.rooms(1).stream().noneMatch(r->r.kind().equals("nest")));
        check("living cap doubled",ChallengeRules.MAX_LIVING==96);
        check("ten enemies have five second baseline",ChallengeRules.batchSeconds(10,1)==5);
        check("twenty enemies have twenty second baseline",ChallengeRules.batchSeconds(20,1)==20);
        check("fast clears increase pressure",ChallengeRules.adaptTempo(1,40,100,true)<1);
        check("slow clears only bounded relief",ChallengeRules.adaptTempo(1,100,100,false)<=1.25);
        var pistol=ChallengeGunPower.calculate(6,1,400,17,3.5,"pistol",0,0,0,1.5);
        var rifle=ChallengeGunPower.calculate(9,1,650,30,3.5,"rifle",0,0,0.2,1.5);
        var extended=ChallengeGunPower.calculate(9,1,650,60,3.5,"rifle",0,0,0.2,1.5);
        var rocket=ChallengeGunPower.calculate(20,1,150,1,3,"rpg",120,3,0,1);
        check("power price follows damage and RPM not materials",rifle.price()>pistol.price());
        check("bigger magazine improves sustained output",extended.sustainedDps()>rifle.sustainedDps()&&extended.price()>=rifle.price());
        check("explosives include crowd damage",rocket.shot()>20&&rocket.price()>rifle.price());
        return passed;
    }
}
