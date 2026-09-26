package net.muxigame.core.feature.tasks.integration;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.muxigame.core.feature.tasks.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Server-confirmed damaging bullet hits. TaCZ fires either Post OR Kill, not both on a lethal hit. */
public final class TaczTaskHooks {
    private final DailyTasksFeature feature;
    private record Hit(UUID shooter,UUID bullet,UUID victim) {}
    private final Map<Hit,Integer> damage=new HashMap<>();
    private final Map<Hit,Integer> counted=new HashMap<>();
    public TaczTaskHooks(DailyTasksFeature feature,IEventBus bus) {
        this.feature=feature;
        bus.addListener(this::damage); bus.addListener(EventPriority.LOWEST,this::hurt);
        bus.addListener(this::kill); bus.addListener(this::tick);
    }
    private void damage(LivingDamageEvent.Post event) {
        Entity direct=event.getSource().getDirectEntity();
        ServerPlayer p=TaskOwnership.credit(event.getSource());
        if(p!=null && direct!=null && direct!=p && event.getNewDamage()>0
            && feature.acceptsCombat(p,event.getEntity())) {
            if(damage.size()>=4096) damage.clear();
            damage.put(new Hit(p.getUUID(),direct.getUUID(),event.getEntity().getUUID()),p.server.getTickCount());
        }
    }
    private void hurt(EntityHurtByGunEvent.Post event) {
        if(event.getLogicalSide()!=LogicalSide.SERVER || event.isCanceled()) return;
        if(event.getHurtEntity() instanceof LivingEntity target)
            hit(event.getAttacker(),event.getBullet(),target,event.isHeadShot(),false);
    }
    private void kill(EntityKillByGunEvent event) {
        if(event.getLogicalSide()!=LogicalSide.SERVER) return;
        hit(event.getAttacker(),event.getBullet(),event.getKilledEntity(),event.isHeadShot(),true);
    }
    private void hit(LivingEntity attacker,Entity bullet,LivingEntity target,boolean head,boolean killed) {
        ServerPlayer player=TaskOwnership.credit(attacker);
        if(player==null || bullet==null || !feature.acceptsCombat(player,target)) return;
        Hit key=new Hit(player.getUUID(),bullet.getUUID(),target.getUUID());
        Integer at=damage.remove(key);
        // A nominal TaCZ Post event may exist even if another mod prevented the damage.
        if(at==null || player.server.getTickCount()-at>2 || counted.containsKey(key)) return;
        if(counted.size()>=4096) counted.clear(); counted.put(key,player.server.getTickCount());
        // Practice is personal; a helper's firearm kill still advances its owner's gun-hunt/kill tasks.
        if(attacker==player) {
            feature.combatProgress(player,TaskCatalog.Kind.GUN_HIT,target,0);
            if(head) feature.combatProgress(player,TaskCatalog.Kind.GUN_HEADSHOT,target,0);
        }
        if(killed && target.isDeadOrDying()) feature.gunKill(player,target);
    }
    private void tick(ServerTickEvent.Post event) {
        int now=event.getServer().getTickCount();
        if(now%20!=0) return;
        damage.entrySet().removeIf(e->now-e.getValue()>2);
        counted.entrySet().removeIf(e->now-e.getValue()>200);
    }
    public void clear() { damage.clear(); counted.clear(); }
    public static ItemStack ammo(String id,int count) {
        ResourceLocation key=ResourceLocation.parse(id);
        if(TimelessAPI.getCommonAmmoIndex(key).isEmpty()) throw new IllegalArgumentException("Unknown TaCZ ammunition: "+id);
        return AmmoItemBuilder.create().setId(key).setCount(count).build();
    }
}
