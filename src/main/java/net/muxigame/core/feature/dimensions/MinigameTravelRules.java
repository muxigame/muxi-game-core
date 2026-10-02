package net.muxigame.core.feature.dimensions;

/** Exact arena/return-record pairs for the existing server-owned room starts. */
public final class MinigameTravelRules {
    private MinigameTravelRules() {}
    public static String returnKey(String game,String destination) {
        return switch(game) {
            case "zombie-challenge" -> destination.equals("muxi_game_core:quarantine") ? "muxi_challenge_return" : "";
            case "outbreak" -> destination.equals("muxi_outbreak:campaign") ? "muxi_outbreak_return_v1" : "";
            case "horse_racing" -> destination.equals("muxi_minigames:horse_lab") || destination.equals("muxi_minigames:horse_stadium_lab") ? "muxi_horse_spectator_return_v1" : "";
            case "flight" -> destination.equals("muxi_minigames:flight_lab") ? "muxi_flight_return_v1" : "";
            default -> "";
        };
    }
}
