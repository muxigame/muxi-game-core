package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.ModDamageTypes;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.registries.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.nbt.CompoundTag;
import net.muxigame.core.feature.tasks.*;
import net.muxigame.core.feature.tasks.integration.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.commons.lang3.tuple.Pair;
import top.theillusivec4.champions.api.ChampionsApi;
import top.theillusivec4.champions.common.api.ChampionsRegistries;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Real installed mods, registries, native hurt pipeline and their published events, in an isolated world.
 * Gun hit positions are a fixture, not an end-to-end test of mouse input or bullet flight. */
@Mod("muxi_tasks_smoke")
public final class TaskIntegrationSmoke {
    private final List<String> passed=new ArrayList<>();
    private int ticks;
    private boolean done;
    private MinecraftServer server;
    private ServerPlayer player;
    private DailyTasksFeature feature;
    private LivingEntity cancelDeath,lastVictim;
    private EntityKineticBullet lastBullet;
    private DamageSource lastDamage;
    private final ResourceLocation gun=ResourceLocation.parse("tacz:glock_17");
    public TaskIntegrationSmoke() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,this::cancelDeath);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void cancelDeath(LivingDeathEvent event) { if(event.getEntity()==cancelDeath) event.setCanceled(true); }
    private void check(String name,boolean ok) { if(!ok) throw new AssertionError(name); passed.add(name); }
    private DailyTaskState state() { return DailyTaskState.parse(player.getPersistentData().getCompound("PlayerPersisted").getString("muxi_daily_tasks")); }
    private int progress(String id) { return state().find(id).progress(); }
    private ServerPlayer player() {
        var profile=new GameProfile(UUID.fromString("6145ded7-9d7c-4ad6-bcf7-c82824d95488"),"MuxiIntegrationQA");
        var p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        var transport=new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(transport);
        p.connection=new ServerGamePacketListenerImpl(server,transport,p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) {}
        };
        return p;
    }
    private Zombie zombie() { var z=new Zombie(EntityType.ZOMBIE,server.overworld()); z.setNoAi(true); return z; }
    private <T extends LivingEntity> T champion(T entity,int tier) {
        var rank=ChampionsApi.get().getTierByLevel(tier).orElseThrow();
        ChampionsRegistries.builder().trySpawnWithAffixes(entity,rank,List.of(),entity.getRandom(),null).orElseThrow();
        check("native champion tier "+tier+" attached",ChampionTaskHooks.tier(entity)==tier);
        return entity;
    }
    private DamageSource damage(Entity bullet) {
        return new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.BULLET),bullet,player);
    }
    private void post(LivingEntity target,EntityKineticBullet bullet,DamageSource source,boolean head,boolean killed,LogicalSide side) {
        if(killed) NeoForge.EVENT_BUS.post(new EntityKillByGunEvent(bullet,target,player,gun,gun,2,Pair.of(source,source),head,2,side));
        else NeoForge.EVENT_BUS.post(new EntityHurtByGunEvent.Post(bullet,target,player,gun,gun,2,Pair.of(source,source),head,2,side));
    }
    private void shoot(LivingEntity target,boolean head,boolean killed,float amount) {
        var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,server.overworld()); bullet.setOwner(player);
        var source=damage(bullet); target.invulnerableTime=0; target.hurt(source,amount);
        post(target,bullet,source,head,killed,LogicalSide.SERVER);
        lastVictim=target; lastBullet=bullet; lastDamage=source;
    }
    private int inventoryCount(Item item) { return player.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private void finish(Throwable error) {
        done=true;
        try {
            Map<String,Object> out=new LinkedHashMap<>(); out.put("passed",passed); out.put("success",error==null);
            out.put("fixture","native damage + installed mod events; synthetic hit positions, no real client");
            if(error!=null) { out.put("error",error.toString()); error.printStackTrace(); }
            Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch(Exception e) { e.printStackTrace(); }
        server.halt(false);
    }
    private void tick(ServerTickEvent.Post event) {
        if(done) return; server=event.getServer(); if(++ticks%10!=0 || ticks<20) return;
        try {
            feature=DailyTasksFeature.active(server);
            switch(ticks) {
                case 20 -> {
                    check("core active with TaCZ Champions Create",feature!=null);
                    player=player(); feature.request(player,false);
                    check("default installed-mod assignment is 3 plus 1",state().entries().size()==4 && state().entries().get(3).definition.hard());
                    check("real brass item registered",BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("create:brass_ingot")));
                    ItemStack ammo=TaczTaskHooks.ammo("tacz:9mm",60);
                    check("native ammo builder uses actual 9mm ID",ammo.getCount()==60 && IAmmo.getIAmmoOrNull(ammo).getAmmoId(ammo).toString().equals("tacz:9mm"));
                    var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());
                    try {
                        ItemStack.STREAM_CODEC.encode(buffer,ammo); ItemStack copy=ItemStack.STREAM_CODEC.decode(buffer);
                        check("ammo components survive native network codec",ItemStack.matches(ammo,copy) && IAmmo.getIAmmoOrNull(copy).getAmmoId(copy).toString().equals("tacz:9mm"));
                    } finally { buffer.release(); }
                }
                case 30 -> {
                    var catalog=TaskCatalog.load(Path.of(TaskCatalog.FILE));
                    // Fix only the TEST player's assignments so every integration can be exercised in one run.
                    var subset=catalog.available(d->d.id().startsWith("shooting_") || d.id().equals("legendary_zombie"));
                    var assigned=DailyTaskState.create(subset,player.getUUID(),server.overworld().getSeed(),Instant.now(),d->0);
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)); player=player();
                    var root=new CompoundTag(); root.putString("muxi_daily_tasks",assigned.json()); player.getPersistentData().put("PlayerPersisted",root);
                    feature.request(player,false);
                    check("three shooting types plus specific legendary target assigned",state().entries().size()==4 && progress("shooting_hits")==0 && progress("legendary_zombie")==0);
                }
                case 40 -> { shoot(zombie(),false,false,3); check("real damaging body hit counts once",progress("shooting_hits")==1 && progress("shooting_headshots")==0); }
                case 50 -> {
                    post(lastVictim,lastBullet,lastDamage,false,false,LogicalSide.SERVER);
                    post(lastVictim,lastBullet,lastDamage,false,false,LogicalSide.CLIENT);
                    check("duplicate and client-side reports do not count",progress("shooting_hits")==1);
                }
                case 60 -> {
                    var b=new EntityKineticBullet(EntityKineticBullet.TYPE,server.overworld()); b.setOwner(player);
                    post(zombie(),b,damage(b),false,false,LogicalSide.SERVER);
                    check("nominal gun report without real damage rejected",progress("shooting_hits")==1);
                }
                case 70 -> {
                    var arrow=new Arrow(EntityType.ARROW,server.overworld()); arrow.setOwner(player);
                    zombie().hurt(server.overworld().damageSources().arrow(arrow,player),3);
                    check("bow damage is not gun training",progress("shooting_hits")==1);
                }
                case 80 -> { shoot(zombie(),true,false,3); check("server-confirmed headshot counts in both counters",progress("shooting_hits")==2 && progress("shooting_headshots")==1); }
                case 90 -> {
                    var target=zombie(); var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,server.overworld()); bullet.setOwner(player); var s=damage(bullet);
                    target.hurt(s,1); target.invulnerableTime=0; target.hurt(s,1); post(target,bullet,s,false,false,LogicalSide.SERVER);
                    check("two damage portions still one bullet hit",progress("shooting_hits")==3);
                }
                case 100 -> { shoot(zombie(),false,true,100000); check("lethal hit also advances training",progress("shooting_hits")==4); }
                case 110 -> {
                    check("confirmed gun death advances gun hunt",progress("shooting_hunt")==1);
                    post(lastVictim,lastBullet,lastDamage,false,true,LogicalSide.SERVER);
                    check("repeated gun kill report ignored",progress("shooting_hunt")==1);
                }
                case 120 -> { cancelDeath=zombie(); shoot(cancelDeath,false,true,100000); }
                case 130 -> { check("canceled native death is NOT a gun kill",progress("shooting_hunt")==1); cancelDeath=null; }
                case 140 -> champion(zombie(),3).hurt(server.overworld().damageSources().playerAttack(player),100000);
                case 150 -> {
                    check("elite tier three fails legendary requirement",progress("legendary_zombie")==0);
                    champion(new Spider(EntityType.SPIDER,server.overworld()),4).hurt(server.overworld().damageSources().playerAttack(player),100000);
                }
                case 160 -> { check("legendary wrong species fails requirement",progress("legendary_zombie")==0); champion(zombie(),4).hurt(server.overworld().damageSources().playerAttack(player),100000); }
                case 170 -> {
                    check("real tier four target death completes hard task",progress("legendary_zombie")==1);
                    check("melee champion death not a gun kill",progress("shooting_hunt")==1);
                    check("passive targets excluded",!feature.acceptsCombat(player,EntityType.COW.create(server.overworld())));
                    Wolf pet=new Wolf(EntityType.WOLF,server.overworld()); pet.setTame(true,true); pet.setOwnerUUID(player.getUUID());
                    check("pets excluded",!feature.acceptsCombat(player,pet));
                    check("players excluded",!feature.acceptsCombat(player,player));
                }
                case 180 -> {
                    for(int i=0;i<65;i++) shoot(zombie(),false,false,1);
                    int hitGoal=state().find("shooting_hits").definition.goal();
                    check("actual event progress capped at assigned goal",progress("shooting_hits")==hitGoal);
                    feature.claim(player,state().day().toString(),"shooting_hits");
                    check("training claim adds one level at level zero",player.experienceLevel==1);
                    ItemStack ammo=player.getInventory().items.stream().filter(s->IAmmo.getIAmmoOrNull(s)!=null).findFirst().orElseThrow();
                    var hitRewards=state().find("shooting_hits").definition.rewards();
                    int ammoExpected=hitRewards.stream().filter(r->r.has("ammoId")).findFirst().orElseThrow().get("count").getAsInt();
                    int powderExpected=hitRewards.stream().filter(r->r.get("id").getAsString().equals("minecraft:gunpowder")).findFirst().orElseThrow().get("count").getAsInt();
                    check("training grants assigned usable 9mm stack",ammo.getCount()==ammoExpected && IAmmo.getIAmmoOrNull(ammo).getAmmoId(ammo).toString().equals("tacz:9mm"));
                    check("training grants assigned powder",inventoryCount(Items.GUNPOWDER)==powderExpected);
                }
                case 190 -> {
                    int powder=inventoryCount(Items.GUNPOWDER);
                    feature.claim(player,state().day().toString(),"shooting_hits");
                    check("training cannot be claimed twice",player.experienceLevel==1 && inventoryCount(Items.GUNPOWDER)==powder);
                }
                case 200 -> {
                    for(int i=0;i<20;i++) shoot(zombie(),true,false,1);
                    feature.claim(player,state().day().toString(),"shooting_headshots");
                    int brassExpected=state().find("shooting_headshots").definition.rewards().stream()
                        .filter(r->r.get("id").getAsString().equals("create:brass_ingot")).findFirst().orElseThrow().get("count").getAsInt();
                    check("headshot task grants assigned real brass ingots",inventoryCount(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:brass_ingot")))==brassExpected);
                    check("headshot task grants another level",player.experienceLevel==2);
                }
                case 210 -> {
                    feature.claim(player,state().day().toString(),"legendary_zombie");
                    check("hard task grants its fixed random apple count",inventoryCount(Items.ENCHANTED_GOLDEN_APPLE)==state().find("legendary_zombie").definition.rewards().get(0).get("count").getAsInt());
                    check("hard task grants eight emeralds and one level",inventoryCount(Items.EMERALD)==8 && player.experienceLevel==3);
                }
                case 220 -> {
                    feature.reroll(player,state().day().toString(),"legendary_zombie");
                    check("claimed hard task cannot yield a second challenge",state().find("legendary_zombie").claimed() && state().rerollsRemaining()==1);
                    finish(null);
                }
                default -> {}
            }
        } catch(Throwable error) { finish(error); }
    }
}
