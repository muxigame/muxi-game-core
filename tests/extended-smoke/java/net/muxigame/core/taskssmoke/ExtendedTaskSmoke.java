package net.muxigame.core.taskssmoke;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.ModDamageTypes;
import io.github.jamalam360.rightclickharvest.RightClickHarvest;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.muxigame.core.feature.tasks.*;
import net.muxigame.core.feature.tasks.integration.ChampionTaskHooks;
import net.neoforged.bus.api.*;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.*;
import org.apache.commons.lang3.tuple.Pair;
import top.theillusivec4.champions.api.ChampionsApi;
import top.theillusivec4.champions.common.api.ChampionsRegistries;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Real installed mod entities, real damage/break/harvest APIs; no real players or production world. */
@Mod("muxi_tasks_smoke")
public final class ExtendedTaskSmoke {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("muxi_tasks_smoke");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("muxi_tasks_smoke");
    private static final DeferredBlock<Block> FLOWER=BLOCKS.registerSimpleBlock("test_flower",BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY));
    static { ITEMS.registerSimpleBlockItem(FLOWER); }
    private final List<String> passed=new ArrayList<>();
    private MinecraftServer server;
    private DailyTasksFeature feature;
    private ServerPlayer player,other;
    private LivingEntity maid,golem,otherMaid,lastVictim,canceledDeath;
    private BlockPos canceledDrops;
    private int ticks;
    private boolean done;
    private static final BlockPos P=new BlockPos(4,75,4);
    public ExtendedTaskSmoke(IEventBus modBus) {
        BLOCKS.register(modBus); ITEMS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(LivingDeathEvent e)->{if(e.getEntity()==canceledDeath)e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,(BlockDropsEvent e)->{if(e.getPos().equals(canceledDrops))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(String name,boolean ok) { if(!ok) throw new AssertionError(name); passed.add(name); }
    private DailyTaskState state(ServerPlayer p) { return DailyTaskState.parse(p.getPersistentData().getCompound("PlayerPersisted").getString("muxi_daily_tasks")); }
    private int progress(String task) { return state(player).find(task).progress(); }
    @SuppressWarnings("unchecked")
    private ServerPlayer makePlayer(String suffix) throws Exception {
        UUID id=UUID.nameUUIDFromBytes(("MuxiTest-"+suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var profile=new GameProfile(id,"MuxiQA"+suffix); var p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        var transport=new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(transport);
        p.connection=new ServerGamePacketListenerImpl(server,transport,p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) {}
        };
        // Register the in-memory fixture in the native UUID lookup, so real maid/golem owner APIs resolve it.
        var f=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); f.setAccessible(true);
        ((Map<UUID,ServerPlayer>)f.get(server.getPlayerList())).put(id,p);
        // Do not put the fixture in the connected-player tick list; requests below drive its task sync.
        return p;
    }
    private void assign(ServerPlayer p,String...ids) throws Exception {
        var base=TaskCatalog.load(Path.of(TaskCatalog.FILE)); Set<String> wanted=Set.of(ids);
        var subset=base.available(d->wanted.contains(d.id()));
        List<TaskCatalog.Definition> definitions=new ArrayList<>();
        for(var d:subset.pool()) {
            JsonObject j=d.json();
            if(d.id().equals("harvest_crops")) {j.addProperty("goal",16);j.remove("goalMax");}
            if(d.id().equals("gather_flowers")) {j.addProperty("goal",4);j.remove("goalMax");}
            definitions.add(TaskCatalog.definition(j));
        }
        subset=new TaskCatalog(subset.enabled(),subset.zone(),subset.resetHour(),subset.dailyCount(),subset.hardCount(),definitions);
        var assigned=DailyTaskState.create(subset,p.getUUID(),server.overworld().getSeed(),Instant.now(),d->0);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
        CompoundTag root=new CompoundTag();root.putString("muxi_daily_tasks",assigned.json());p.getPersistentData().put("PlayerPersisted",root);
        feature.request(p,false);
    }
    private LivingEntity entity(String id) {
        var key=ResourceLocation.parse(id); check("native entity registered: "+id,BuiltInRegistries.ENTITY_TYPE.containsKey(key));
        LivingEntity e=(LivingEntity)BuiltInRegistries.ENTITY_TYPE.get(key).create(server.overworld());
        if(e instanceof Mob mob)mob.setNoAi(true);return e;
    }
    private LivingEntity owned(String id,ServerPlayer owner) throws Exception {
        LivingEntity e=entity(id);
        e.getClass().getMethod("setOwnerUUID",UUID.class).invoke(e,owner.getUUID());
        if(e instanceof TamableAnimal t)t.setTame(true,true);
        check("real owner resolved: "+id,TaskOwnership.credit(e)==owner);return e;
    }
    private LivingEntity champion(String id,int tier) {
        LivingEntity e=entity(id);var t=ChampionsApi.get().getTierByLevel(tier).orElseThrow();
        ChampionsRegistries.builder().trySpawnWithAffixes(e,t,List.of(),e.getRandom(),null).orElseThrow();
        check("actual champion tier "+tier+": "+id,ChampionTaskHooks.tier(e)==tier);return e;
    }
    private void kill(LivingEntity attacker,LivingEntity target) {
        target.hurt(server.overworld().damageSources().mobAttack(attacker),100000);lastVictim=target;
    }
    private void shoot(LivingEntity attacker,LivingEntity target,boolean lethal) {
        var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,server.overworld());bullet.setOwner(attacker);
        var source=new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModDamageTypes.BULLET),bullet,attacker);
        target.invulnerableTime=0;target.hurt(source,lethal?100000:1);
        var gun=ResourceLocation.parse("tacz:glock_17");
        if(lethal)NeoForge.EVENT_BUS.post(new EntityKillByGunEvent(bullet,target,attacker,gun,gun,2,Pair.of(source,source),true,2,LogicalSide.SERVER));
        else NeoForge.EVENT_BUS.post(new EntityHurtByGunEvent.Post(bullet,target,attacker,gun,gun,2,Pair.of(source,source),true,2,LogicalSide.SERVER));
    }
    private void plant(BlockState state) {
        server.overworld().setBlockAndUpdate(P.below(),Blocks.FARMLAND.defaultBlockState());
        server.overworld().setBlockAndUpdate(P,state);
    }
    private void breakBlock(BlockState state) {
        plant(state);player.gameMode.destroyBlock(P);
    }
    private void finish(Throwable error) {
        done=true;
        try {
            Map<String,Object> out=new LinkedHashMap<>();out.put("success",error==null);out.put("passed",passed);
            out.put("fixture","real maid/golem entities, real damage and block APIs; synthetic players and shot reports");
            if(error!=null){out.put("error",error.toString());error.printStackTrace();}
            Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
        }catch(Exception e){e.printStackTrace();}
        server.halt(false);
    }
    private void tick(ServerTickEvent.Post event) {
        if(done)return;server=event.getServer();if(++ticks%10!=0||ticks<20)return;
        try {
            feature=DailyTasksFeature.active(server);
            switch(ticks) {
                case 20 -> {
                    player=makePlayer("A");other=makePlayer("B");feature.request(player,false);
                    check("all mod task types resolve without disabling feature",state(player).entries().size()==4);
                    maid=owned("touhou_little_maid:maid",player);
                    golem=owned("modulargolems:metal_golem",player);
                    otherMaid=owned("touhou_little_maid:maid",other);
                    check("unowned village golem not credited",TaskOwnership.credit(entity("minecraft:iron_golem"))==null);
                    assign(player,"zombie_patrol","shooting_hunt","shooting_hits","legendary_zombie");
                    assign(other,"zombie_patrol","shooting_hunt","shooting_hits","legendary_zombie");
                }
                case 30 -> kill(maid,entity("minecraft:zombie"));
                case 40 -> {
                    check("maid melee kill credits owner once",progress("zombie_patrol")==1);
                    var arrow=new Arrow(EntityType.ARROW,server.overworld());arrow.setOwner(golem);
                    var z=entity("minecraft:zombie");z.hurt(server.overworld().damageSources().arrow(arrow,golem),100000);
                }
                case 50 -> {
                    check("modular golem projectile credits owner",progress("zombie_patrol")==2);
                    kill(maid,champion("minecraft:drowned",4));
                }
                case 60 -> {
                    check("third OR target plus helper completes legendary",progress("legendary_zombie")==1);
                    kill(otherMaid,entity("minecraft:zombie"));
                }
                case 70 -> {
                    check("another players helper never credits this player",progress("zombie_patrol")==2);
                    check("another helper credits only its owner",state(other).find("zombie_patrol").progress()==1);
                    shoot(maid,entity("minecraft:zombie"),true);
                }
                case 80 -> {
                    check("helper firearm kill advances gun hunt",progress("shooting_hunt")==1);
                    check("helper kill also ordinary task exactly once",progress("zombie_patrol")==3);
                    check("helper does not replace personal shooting practice",progress("shooting_hits")==0);
                    canceledDeath=entity("minecraft:zombie");kill(golem,canceledDeath);
                }
                case 90 -> {
                    check("canceled helper death not counted",progress("zombie_patrol")==3);canceledDeath=null;
                    shoot(player,entity("minecraft:zombie"),false);
                    check("personal hit still counts",progress("shooting_hits")==1);
                }
                case 100 -> {
                    assign(player,"harvest_crops","gather_flowers","food_hamburger","legendary_fairy");
                    breakBlock(Blocks.WHEAT.defaultBlockState());
                }
                case 110 -> {
                    check("immature crop gives zero",progress("harvest_crops")==0);
                    breakBlock(((CropBlock)Blocks.WHEAT).getStateForAge(7));
                }
                case 120 -> {
                    check("native mature wheat break counts one plant",progress("harvest_crops")==1);
                    plant(((CropBlock)Blocks.WHEAT).getStateForAge(7));player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_HOE));
                    RightClickHarvest.onBlockUse(player,server.overworld(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(P),Direction.UP,P,false),false);
                    check("actual right click replants wheat",server.overworld().getBlockState(P).is(Blocks.WHEAT)&&!GatheringTaskHooks.mature(server.overworld().getBlockState(P)));
                }
                case 130 -> {
                    check("native right click counts once",progress("harvest_crops")==2);
                    var cabbage=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:cabbages"));
                    check("real mod crop is CropBlock",cabbage instanceof CropBlock);
                    breakBlock(((CropBlock)cabbage).getStateForAge(((CropBlock)cabbage).getMaxAge()));
                }
                case 140 -> {
                    check("real mod crop break counts",progress("harvest_crops")==3);
                    canceledDrops=P;breakBlock(((CropBlock)Blocks.WHEAT).getStateForAge(7));
                }
                case 150 -> {
                    check("canceled drops do not count harvest",progress("harvest_crops")==3);canceledDrops=null;
                    breakBlock(Blocks.POPPY.defaultBlockState());
                }
                case 160 -> {
                    check("native flower break counted",progress("gather_flowers")==1);
                    player.getInventory().add(new ItemStack(Items.POPPY,64));
                    check("inventory additions do not fake collection",progress("gather_flowers")==1);
                    check("test mod flower participates through tag",FLOWER.get().defaultBlockState().is(BlockTags.FLOWERS));
                    breakBlock(FLOWER.get().defaultBlockState());
                }
                case 170 -> {
                    check("tagged mod flower counted",progress("gather_flowers")==2);
                    var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("farmersdelight:hamburger"));
                    check("real composite-food recipe exists",server.getRecipeManager().byKey(ResourceLocation.parse("farmersdelight:hamburger")).isPresent());
                    player.awardStat(Stats.ITEM_CRAFTED.get(item),state(player).find("food_hamburger").definition.goal());
                    kill(golem,champion("touhou_little_maid:fairy",4));
                }
                case 180 -> {
                    feature.request(player,false);
                    check("mod food crafting-stat completes at assigned goal",progress("food_hamburger")==state(player).find("food_hamburger").definition.goal());
                    check("actual legendary fairy helper kill completes",progress("legendary_fairy")==1);
                }
                case 190 -> {
                    int expected=state(player).find("legendary_fairy").definition.rewards().get(0).get("count").getAsInt();
                    feature.claim(player,state(player).day().toString(),"legendary_fairy");
                    check("claim grants the previewed 1..2 apples",player.getInventory().items.stream().filter(s->s.is(Items.ENCHANTED_GOLDEN_APPLE)).mapToInt(ItemStack::getCount).sum()==expected);
                    check("single hard reward claimed",state(player).find("legendary_fairy").claimed());
                }
                case 200 -> {
                    int before=player.getInventory().items.stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum();
                    int expected=state(player).find("food_hamburger").definition.rewards().get(0).get("count").getAsInt();
                    feature.claim(player,state(player).day().toString(),"food_hamburger");
                    check("food task rewards assigned 4..8 emeralds not food",player.getInventory().items.stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum()==before+expected && expected>=4 && expected<=8);
                    finish(null);
                }
                default -> {}
            }
        }catch(Throwable e){finish(e);}
    }
}
