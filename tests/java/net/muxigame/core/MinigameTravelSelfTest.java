package net.muxigame.core;

import net.muxigame.core.feature.dimensions.MinigameTravelRules;

public final class MinigameTravelSelfTest {
    public static int run() {
        String[][] pairs={{"zombie-challenge","muxi_game_core:quarantine","muxi_challenge_return"},
            {"outbreak","muxi_outbreak:campaign","muxi_outbreak_return_v1"},
            {"tower_defense","muxi_tower_defense:board","muxi_tower_return_v1"},
            {"horse_racing","muxi_minigames:horse_lab","muxi_horse_spectator_return_v1"},
            {"horse_racing","muxi_minigames:horse_stadium_lab","muxi_horse_spectator_return_v1"},
            {"flight","muxi_minigames:flight_lab","muxi_flight_return_v1"}};
        int checks=0;
        for(var pair:pairs) {
            if(!MinigameTravelRules.returnKey(pair[0],pair[1]).equals(pair[2]))throw new AssertionError("Missing existing arena "+pair[1]);checks++;
            for(String target:new String[]{"minecraft:the_nether","minecraft:the_end","muxi_game_core:adventure","muxi_game_core:overworld","other:arena"}) {
                if(!MinigameTravelRules.returnKey(pair[0],target).isEmpty())throw new AssertionError("Ordinary world escape "+target);checks++;
            }
            for(var other:pairs)if(!pair[0].equals(other[0])) {
                if(!MinigameTravelRules.returnKey(pair[0],other[1]).isEmpty())throw new AssertionError("Another game's arena "+other[1]);checks++;
            }
        }
        if(!MinigameTravelRules.returnKey("unknown","muxi_game_core:quarantine").isEmpty())throw new AssertionError("Unknown game allowed");
        return checks+1;
    }
    public static void main(String[] args) {System.out.println("Minigame travel boundaries passed: "+run());}
}
