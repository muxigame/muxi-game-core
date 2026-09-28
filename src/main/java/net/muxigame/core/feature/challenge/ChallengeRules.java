package net.muxigame.core.feature.challenge;

import java.util.*;

/** Pure, bounded rules shared by the lobby and server. No client supplied scores or rewards. */
public final class ChallengeRules {
    private ChallengeRules() {}
    public static final int MAX_ROOMS=4, MAX_PLAYERS=4, WAVE_SECONDS=300;
    public static final int BATCH_SECONDS=25, MAX_LIVING=48;
    public enum Difficulty {
        NORMAL("普通",5,1.0,1.0,1), HARD("困难",10,1.5,1.3,2),
        EXPERT("专家",15,2.1,1.7,3), NIGHTMARE("噩梦",20,2.8,2.1,4);
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
        return boss(wave,d)?"特殊波次 · 感染暴君":wave%3==0?"特殊波次 · 疾行尸群":wave%4==0?"特殊波次 · 装甲突袭":"僵尸入侵";
    }
    public static int count(int wave,int players) { return Math.min(120,16+wave*4+Math.max(0,Math.min(4,players)-1)*8); }
    public static int batchSize(int wave,int players){return Math.min(20,8+Math.max(0,wave-1)/4*2+Math.max(0,Math.min(4,players)-1)*2);}
    public static double health(Difficulty d,int wave,boolean boss) { return (boss?180:20)*d.health*(1+0.07*(wave-1)); }
    public static double damage(Difficulty d,int wave,boolean boss) { return (boss?7:3)*d.damage*(1+0.025*(wave-1)); }
    public static int score(Difficulty d,int kills,int waves,int seconds,boolean won) {
        return Math.max(0,(kills*10+waves*100+(won?Math.max(0,1800-seconds)/3+500:0))*d.reward);
    }
    public static int rewardGrade(int score,Difficulty d) { return Math.max(1,Math.min(4,1+score/Math.max(1,d.waves*180*d.reward))); }
    public static String rank(int score,Difficulty d) { return switch(rewardGrade(score,d)) {case 4->"S";case 3->"A";case 2->"B";default->"C";}; }
    public static final List<String> TASKS=List.of("首次防线：完成一次挑战","清剿行动：累计击败 100 只入侵僵尸","精英防线：完成困难及以上挑战");
}
