package net.muxigame.core.feature.spawning;
/** Only the currently verified home resource ID is restricted. Existing entities are not a population source. */
public final class HomeSpawnRules {
 public static boolean deny(boolean home,boolean loaded,boolean worldGeneration,String freshReason,HomeSpawnContext.Origin origin){
  if(!home||loaded||origin==HomeSpawnContext.Origin.TRAVEL)return false;
  if(worldGeneration||origin==HomeSpawnContext.Origin.AMBIENT)return true;
  if(freshReason!=null&&switch(freshReason){case "NATURAL","CHUNK_GENERATION","STRUCTURE","PATROL","REINFORCEMENT"->true;default->false;})return true;
  if(origin==HomeSpawnContext.Origin.PLAYER||origin==HomeSpawnContext.Origin.SPAWNER)return false;
  if(freshReason==null)return true;
  return switch(freshReason){case "BREEDING","CONVERSION","BUCKET","SPAWN_EGG","DISPENSER"->false;default->true;};
 }
 private HomeSpawnRules(){}
}
