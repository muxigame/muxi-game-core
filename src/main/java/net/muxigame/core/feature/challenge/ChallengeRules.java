package net.muxigame.core.feature.challenge;

import java.util.*;

/** Pure, bounded rules shared by the lobby and server. No client supplied scores or rewards. */
public final class ChallengeRules {
    private ChallengeRules() {}
    public static final int MAX_ROOMS=4, MAX_PLAYERS=4, WAVE_SECONDS=300;
    public static final int BATCH_SECONDS=20, MAX_LIVING=96, AMMO_COOLDOWN=200, AMMO_HOLD=10, EXCHANGE_RATE=500;
    public enum Difficulty {
        NORMAL("普通",5,1.0,1.0,1), HARD("困难",10,1.5,1.3,2),
        EXPERT("专家",15,2.1,1.7,3), NIGHTMARE("噩梦",20,2.8,2.1,4), EXTREME("极限",25,3.4,2.5,5);
        public final String title;
        public final int waves, reward;
        public final double health, damage;
        Difficulty(String title,int waves,double health,double damage,int reward) {
            this.title=title;this.waves=waves;this.health=health;this.damage=damage;this.reward=reward;
        }
        public static Difficulty parse(String s) { return valueOf(s.toUpperCase(Locale.ROOT)); }
    }
    public static boolean boss(int wave,Difficulty d) { return wave%5==0 || wave==d.waves; }
    public static String waveName(int wave,Difficulty d) {
        return boss(wave,d)?"Boss围攻 · 击杀全部Boss停止增援":wave%3==0?"特殊波次 · 混合感染者":wave%4==0?"特殊波次 · 装甲突袭":"僵尸入侵";
    }
    public static int count(int wave,int players) { return Math.min(120,16+wave*4+Math.max(0,Math.min(4,players)-1)*8); }
    // No solo-first-wave accommodation: the same team-oriented quota applies to every party.
    public static int batchSize(int wave,int players){return Math.min(20,10+Math.max(0,wave-1)/3*2+Math.max(0,players-1)*2);}
    public static int batchSeconds(int count,double tempo){return Math.max(3,Math.min(22,(int)Math.round((5+Math.max(0,Math.min(10,count-10))*1.5)*tempo)));}
    public static double adaptTempo(double previous,int elapsed,int expected,boolean cleared){double ratio=(double)elapsed/Math.max(1,expected);double desired=previous*(cleared?(ratio<0.75?0.8:0.95):1.1);return Math.max(0.55,Math.min(1.1,previous*0.6+desired*0.4));}
    public static int spawnFloor(int wave,int ordinal){int roll=Math.floorMod(ordinal*37,100);return wave>=15&&roll>=95?2:wave>=8&&roll>=75?1:0;}
    public enum Enemy {
        ZOMBIE("感染尸群",20,3,0.21,false), RUNNER("疾行幼尸",14,3,0.30,false),
        CREEPER("爆破感染者",20,0,0.17,false), PIGLIN("狂暴猪人",32,8,0.16,false),
        IRON("失控铁傀儡",160,9,0.15,true), MODULAR("装配重型傀儡",320,12,0.15,true), WARDEN("深层坚守者",480,16,0.17,true);
        public final String title;public final double health,damage,speed;public final boolean boss;
        Enemy(String title,double health,double damage,double speed,boolean boss){this.title=title;this.health=health;this.damage=damage;this.speed=speed;this.boss=boss;}
    }
    public static List<Enemy> bosses(int wave,Difficulty d){if(!boss(wave,d))return List.of();Enemy main=wave>=15?Enemy.WARDEN:wave>=10||d==Difficulty.EXTREME?Enemy.MODULAR:Enemy.IRON;return d==Difficulty.EXTREME?List.of(main,Enemy.IRON,Enemy.IRON):List.of(main);}
    public static Enemy enemy(int wave,Difficulty d,int ordinal){var bosses=bosses(wave,d);if(ordinal<bosses.size())return bosses.get(ordinal);int roll=Math.floorMod(ordinal*31+wave*7,100);return wave>=4&&roll<8?Enemy.CREEPER:wave>=6&&roll<16?Enemy.PIGLIN:wave>=3&&roll<26?Enemy.RUNNER:Enemy.ZOMBIE;}
    public static double health(Difficulty d,int wave,boolean boss) { return (boss?180:20)*d.health*(1+0.07*(wave-1)); }
    public static double damage(Difficulty d,int wave,boolean boss) { return (boss?7:3)*d.damage*(1+0.025*(wave-1)); }
    public static int score(Difficulty d,int kills,int waves,int seconds,boolean won) {
        return Math.max(0,(kills*10+waves*100+(won?Math.max(0,1800-seconds)/3+500:0))*d.reward);
    }
    public static int rewardGrade(int score,Difficulty d) { return Math.max(1,Math.min(4,1+score/Math.max(1,d.waves*180*d.reward))); }
    public static String rank(int score,Difficulty d) { return switch(rewardGrade(score,d)) {case 4->"S";case 3->"A";case 2->"B";default->"C";}; }
    public static final List<String> TASKS=List.of("首次防线：完成一次挑战","清剿行动：累计击败 100 只入侵僵尸","精英防线：完成困难及以上挑战");
}
