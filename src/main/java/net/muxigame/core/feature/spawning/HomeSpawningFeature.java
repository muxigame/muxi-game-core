package net.muxigame.core.feature.spawning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.Level;
import net.muxigame.core.feature.ServerFeature;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
/** Home-only admission guard. Saved entities and dimension travel never become fresh ambient population. */
public final class HomeSpawningFeature implements ServerFeature {
 private static volatile boolean active;
 public static boolean home(Level level){return net.muxigame.core.feature.dimensions.WorldDimensions.home(level.dimension());}
 public static boolean protectedHome(Level level){return active&&!level.isClientSide&&home(level);}
 public static boolean blocked(Level level,MobSpawnType reason,EntityType<?> type){
  // Eligibility is not admission. Some spell constructors set their summoner only after initialization.
  // Every fresh insertion still needs deliberate provenance, including MOB_SUMMONED.
  if(reason==MobSpawnType.MOB_SUMMONED&&HomeSpawnContext.current()!=HomeSpawnContext.Origin.AMBIENT)return false;
  return HomeSpawnRules.deny(home(level),false,false,reason==null?null:reason.name(),HomeSpawnContext.current());}
 public String id(){return "home-spawning";}
 public void register(IEventBus bus){active=true;bus.addListener(EventPriority.LOWEST,this::phantoms);bus.addListener(EventPriority.LOWEST,this::placement);bus.addListener(EventPriority.LOWEST,this::position);bus.addListener(EventPriority.LOWEST,true,this::finalizeSpawn);bus.addListener(EventPriority.LOWEST,this::admission);}
 private void phantoms(PlayerSpawnPhantomsEvent event){if(protectedHome(event.getEntity().level()))event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);}
 private void placement(MobSpawnEvent.SpawnPlacementCheck event){if(blocked(event.getLevel().getLevel(),event.getSpawnType(),event.getEntityType()))event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);}
 private void position(MobSpawnEvent.PositionCheck event){if(blocked(event.getLevel().getLevel(),event.getSpawnType(),event.getEntity().getType()))event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);}
 private void finalizeSpawn(FinalizeSpawnEvent event){if(blocked(event.getLevel().getLevel(),event.getSpawnType(),event.getEntity().getType())){event.setSpawnCancelled(true);event.setCanceled(true);}}
 public static boolean deliberateMagicSummon(Mob mob){
  if(!mob.getClass().getName().startsWith("io.redspace.ironsspellbooks.entity."))return false;
  try{return mob.getClass().getMethod("getSummoner").invoke(mob) instanceof net.minecraft.server.level.ServerPlayer;}catch(ReflectiveOperationException absent){return false;}
 }
 public static boolean denyFresh(Level level,Entity entity){
  if(!(entity instanceof Mob mob)||!protectedHome(level))return false;
  MobSpawnType reason=mob instanceof FreshMobSpawn p?p.muxi$freshSpawnReason():null;
  var origin=HomeSpawnContext.current();
  if(origin==null&&deliberateMagicSummon(mob))origin=HomeSpawnContext.Origin.PLAYER;
  return HomeSpawnRules.deny(true,false,false,reason==null?null:reason.name(),origin);
 }
 private void admission(EntityJoinLevelEvent event){
  if(!(event.getLevel() instanceof ServerLevel)||!(event.getEntity() instanceof Mob mob))return;
  FreshMobSpawn provenance=mob instanceof FreshMobSpawn p?p:null;
  MobSpawnType reason=provenance==null?null:provenance.muxi$freshSpawnReason();
  boolean deny=!event.loadedFromDisk()&&denyFresh(event.getLevel(),mob);
  if(deny)event.setCanceled(true);
  else if(protectedHome(event.getLevel())&&mob instanceof SkeletonHorse horse&&horse.isTrap())horse.setTrap(false);
  if(provenance!=null)provenance.muxi$clearFreshSpawnReason();
 }
 public void close(){active=false;}
}
