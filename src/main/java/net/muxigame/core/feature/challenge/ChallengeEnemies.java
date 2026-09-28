package net.muxigame.core.feature.challenge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Item;
import java.util.*;

/** Only challenge-owned entities are configured; optional golem API is resolved lazily. */
public final class ChallengeEnemies {
    private ChallengeEnemies(){}
    public static Mob create(ServerLevel level,ChallengeRules.Enemy kind,ChallengeRules.Difficulty difficulty,int wave,ChallengeArena arena){
        Mob mob=switch(kind){
            case ZOMBIE,RUNNER -> EntityType.ZOMBIE.create(level);
            case CREEPER -> EntityType.CREEPER.create(level);
            case PIGLIN -> EntityType.ZOMBIFIED_PIGLIN.create(level);
            case IRON -> EntityType.IRON_GOLEM.create(level);
            case WARDEN -> EntityType.WARDEN.create(level);
            case MODULAR -> modular(level);
        };
        if(mob==null)throw new IllegalStateException("Challenge entity unavailable: "+kind);
        if(mob instanceof Zombie z){z.setBaby(kind==ChallengeRules.Enemy.RUNNER);z.setCanBreakDoors(false);}
        mob.setPersistenceRequired();mob.setCanPickUpLoot(false);
        attribute(mob,Attributes.MAX_HEALTH,kind.health*difficulty.health*(1+0.07*(wave-1)));
        attribute(mob,Attributes.ATTACK_DAMAGE,kind.damage*difficulty.damage*(1+0.025*(wave-1)));
        // Remove the native baby +50% speed modifier too: speed is the final, bounded value.
        attribute(mob,Attributes.MOVEMENT_SPEED,kind.speed);attribute(mob,Attributes.FOLLOW_RANGE,192);
        if(kind.boss)attribute(mob,Attributes.KNOCKBACK_RESISTANCE,0.8);
        mob.setHealth(mob.getMaxHealth());mob.getNavigation().setMaxVisitedNodesMultiplier(3);
        mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
        if(mob instanceof Warden w){
            w.getBrain().removeAllBehaviors();
            // Keep the real sonic attack, but no sniffing, idle wandering or burrowing/despawn behavior.
            w.getBrain().addActivity(net.minecraft.world.entity.schedule.Activity.FIGHT,10,com.google.common.collect.ImmutableList.of(new net.minecraft.world.entity.ai.behavior.warden.SonicBoom()));
            w.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN,net.minecraft.util.Unit.INSTANCE);
        }
        mob.goalSelector.addGoal(0,new ChallengePursuitGoal(mob,arena));
        return mob;
    }
    public static void target(Mob mob,net.minecraft.world.entity.LivingEntity target){if(mob instanceof Warden w)w.setAttackTarget(target);else mob.setTarget(target);}
    private static void attribute(Mob mob,net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> key,double value){var a=mob.getAttribute(key);if(a!=null){a.removeModifiers();a.setBaseValue(value);}}
    @SuppressWarnings("unchecked")
    private static Mob modular(ServerLevel level){
        var id=ResourceLocation.parse("modulargolems:metal_golem");
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(id))return EntityType.IRON_GOLEM.create(level);
        try{
            Mob mob=(Mob)BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
            Class<?> material=Class.forName("dev.xkmc.modulargolems.content.config.GolemMaterial");
            Class<?> configType=Class.forName("dev.xkmc.modulargolems.content.config.GolemMaterialConfig");
            Object config=configType.getMethod("get").invoke(null);
            var stats=(Map<ResourceLocation,HashMap<?,?>>)configType.getField("stats").get(config);
            ResourceLocation iron=stats.keySet().stream().filter(k->k.getPath().equals("iron")).findFirst().orElseThrow();
            // Do not reflect the part enum: its render methods reference client-only PoseStack.
            ArrayList<Object> parts=new ArrayList<>();
            for(String part:List.of("arm","body","arm","legs")){
                var partId=ResourceLocation.parse("modulargolems:metal_golem_"+part);
                if(!BuiltInRegistries.ITEM.containsKey(partId))throw new IllegalStateException("Missing golem part "+partId);
                parts.add(material.getConstructor(HashMap.class,HashMap.class,ResourceLocation.class,Item.class).newInstance(new HashMap<>(stats.get(iron)),new HashMap<>(),iron,BuiltInRegistries.ITEM.get(partId)));
            }
            Class<?> upgrade=Class.forName("dev.xkmc.modulargolems.content.item.data.GolemUpgrade");
            Object faction=Class.forName("dev.xkmc.modulargolems.content.entity.hostile.HostileGolemRegistry").getField("DEFAULT").get(null);
            UUID owner=(UUID)faction.getClass().getField("uuid").get(faction);
            mob.getClass().getMethod("onCreate",ArrayList.class,upgrade,UUID.class).invoke(mob,parts,upgrade.getField("EMPTY").get(null),owner);
            return mob;
        }catch(ReflectiveOperationException|RuntimeException e){throw new IllegalStateException("Unable to initialize challenge modular golem",e);}
    }
}
