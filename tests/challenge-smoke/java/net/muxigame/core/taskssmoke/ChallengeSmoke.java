package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.*;
import net.minecraft.server.level.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.muxigame.core.feature.challenge.*;
import net.muxigame.core.feature.tasks.DailyTasksFeature;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.nio.file.*;
import java.util.*;

@Mod("muxi_tasks_smoke")
public final class ChallengeSmoke {
    private final List<String> passed=new ArrayList<>();
    private int ticks;
    private ServerPlayer p,q;
    private ChallengeFeature.Room room;
    private ListTag original;
    private Zombie scoredVictim;
    private int balanceBeforeKill;
    private Zombie pursuer;
    private Zombie stairWalker;
    private UUID walkerId;
    private final Map<String,Integer> prices=new LinkedHashMap<>();
    private net.minecraft.world.phys.Vec3 pursuitStart,stuckPosition;
    public ChallengeSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);}
    private void check(String name,boolean pass){if(!pass)throw new AssertionError(name);passed.add(name);}
    @SuppressWarnings("unchecked")
    private ServerPlayer player(MinecraftServer server,String name) throws Exception {
        var profile=new GameProfile(UUID.randomUUID(),name);ServerPlayer p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        Connection transport=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(transport);
        p.connection=new ServerGamePacketListenerImpl(server,transport,p,CommonListenerCookie.createInitial(profile,false)){@Override public void send(Packet<?> packet){}};
        p.setGameMode(GameType.SURVIVAL);p.setPos(0,70,0);
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);((List<ServerPlayer>)field.get(server.getPlayerList())).add(p);
        var ids=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");ids.setAccessible(true);((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(p.getUUID(),p);
        server.overworld().addNewPlayer(p);return p;
    }
    private void finish(MinecraftServer server,Throwable error){
        try{var result=new LinkedHashMap<String,Object>();result.put("success",error==null);result.put("passed",passed);result.put("prices",prices);if(error!=null){result.put("error",error.toString());error.printStackTrace();}Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}server.halt(false);
    }
    private void tick(ServerTickEvent.Post e){
        MinecraftServer server=e.getServer();ticks++;
        try{
            var feature=ChallengeFeature.active(server);
            if(ticks==20){
                check("challenge service registered",feature!=null);check("dedicated dimension created from data",server.getLevel(ChallengeArena.DIMENSION)!=null);
                for(var d:ChallengeRules.Difficulty.values()){
                    check("boss final "+d,ChallengeRules.boss(d.waves,d));check("health progression "+d,ChallengeRules.health(d,5,true)>ChallengeRules.health(d,1,false));
                    check("score includes completion "+d,ChallengeRules.score(d,10,5,300,true)>ChallengeRules.score(d,10,5,300,false));
                }
                check("mob count capped",ChallengeRules.count(500,100)==120);
                p=player(server,"ChallengeQA");q=player(server,"ChallengeGuest");
                CompoundTag legacy=new CompoundTag();legacy.putInt("credits",12345);q.getPersistentData().put(ChallengeFeature.STATE,legacy);feature.snapshot(q,"");
                check("legacy points migrate to coins with remainder",q.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==123&&q.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("exchangeRemainder")==45);
                feature.snapshot(q,"");check("currency migration is idempotent",q.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==123);
                ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);sword.set(DataComponents.CUSTOM_NAME,Component.literal("保留组件的原始武器"));sword.setDamageValue(37);
                p.getInventory().setItem(0,sword);p.getInventory().setItem(4,new ItemStack(Items.DIAMOND,13));p.getInventory().selected=4;
                original=p.getInventory().save(new ListTag());
                room=feature.create(p,ChallengeRules.Difficulty.NORMAL);
                check("room starts generating",room.phase==ChallengeFeature.Phase.BUILDING);
                feature.handle(q,"start","");check("guest cannot start",room.phase==ChallengeFeature.Phase.BUILDING);
            }
            if(ticks==30){feature.handle(q,"join",room.id);check("joining without invite denied",room.members.size()==1);}
            if(ticks==40){feature.handle(p,"invite",q.getUUID().toString());check("invite stored",room.invites.containsKey(q.getUUID()));}
            if(ticks==50){feature.handle(q,"join",room.id);check("invited player joins",room.members.contains(q.getUUID()));}
            if(ticks==60){feature.leave(q,"test");check("guest can leave without ending lobby",feature.rooms().size()==1);}
            if(ticks==170||ticks==340||ticks==480)room.arena.build(server.getLevel(ChallengeArena.DIMENSION),ChallengeArena.SIZE*ChallengeArena.SIZE*ChallengeArena.HEIGHT);
            if(ticks==180){
                check("incremental map completes",room.phase==ChallengeFeature.Phase.LOBBY);
                var level=server.getLevel(ChallengeArena.DIMENSION);
                check("floor at spawn is solid",level.getBlockState(room.arena.spawn().below()).isSolidRender(level,room.arena.spawn().below()));
                check("spawn has two blocks headroom",level.isEmptyBlock(room.arena.spawn())&&level.isEmptyBlock(room.arena.spawn().above()));
                for(int floor=0;floor<3;floor++)for(var s:room.arena.spawns(floor))check("safe zombie spawn "+s,level.isEmptyBlock(s)&&level.isEmptyBlock(s.above()));
                check("asymmetric building has fifty two rooms",ChallengeArena.ROOMS.size()==52);
                check("expanded map footprint",ChallengeArena.SIZE==81);
                check("north stair is a physical stair block",level.getBlockState(room.arena.pos(40,1,12)).getBlock() instanceof net.minecraft.world.level.block.StairBlock);
                check("upper stairwell has clearance",level.isEmptyBlock(room.arena.pos(40,10,12)));
                check("ammunition cabinet has real barrel",level.getBlockState(room.arena.ammoStation(0)).is(net.minecraft.world.level.block.Blocks.BARREL));
                check("jump platforms present",!level.isEmptyBlock(room.arena.pos(21,23,54)));
                feature.start(p);
                var routeProbe=EntityType.ZOMBIE.create(level);routeProbe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(192);routeProbe.getNavigation().setMaxVisitedNodesMultiplier(3);
                for(var site:room.arena.sites()){
                    routeProbe.setPos(site.position().getX()+0.5,site.position().getY(),site.position().getZ()+0.5);routeProbe.setOnGround(true);routeProbe.getNavigation().stop();
                    var area=room.arena.roomAt(routeProbe.getX(),routeProbe.getY(),routeProbe.getZ());
                    if(area!=null){var outside=room.arena.outside(area,site.floor());var route=routeProbe.getNavigation().createPath(outside,0);check("spawn portal exits room "+site.name(),route!=null&&route.canReach());routeProbe.setPos(outside.getX()+0.5,outside.getY(),outside.getZ()+0.5);routeProbe.getNavigation().stop();}
                    var hallway=routeProbe.getNavigation().createPath(room.arena.pos(40,1+site.floor()*10,40),0);check("spawn portal connects to main corridor "+site.name(),hallway!=null&&hallway.canReach());
                }
                check("solo waits for client world load",room.phase==ChallengeFeature.Phase.LOADING && room.mobs.isEmpty() && room.alive.size()==1);
                feature.clientReady(p);
                check("full thirty second grace after load acknowledgment",room.phase==ChallengeFeature.Phase.COUNTDOWN && room.timer-server.getTickCount()==600 && room.mobs.isEmpty());
                check("spawn sites announced during preparation",room.preparedWave==1&&room.activeSites.size()==2);
                var firstSites=List.copyOf(room.activeSites);feature.planWave(room,2);check("next wave changes active spawn entrances",!firstSites.equals(room.activeSites));feature.planWave(room,1);
                room.arena.lamps(level,room.activeSites,true);
                check("active entrances illuminate warning lamps",room.activeSites.stream().allMatch(s->level.getBlockState(s.lamp()).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)));
                check("inactive entrances stay dark",room.arena.sites().stream().filter(s->!room.activeSites.contains(s)).allMatch(s->!level.getBlockState(s.lamp()).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT)));
                room.arena.lamps(level,room.activeSites,false);
                check("player enters isolated dimension",ChallengeFeature.dimension(p.level()));
                check("original inventory persisted",ChallengeInventory.pending(p));
                check("original diamonds not in kit",p.getInventory().items.stream().noneMatch(s->s.is(Items.DIAMOND)));
                check("adventure mode active",p.gameMode.getGameModeForPlayer()==GameType.ADVENTURE);
                if(net.neoforged.fml.ModList.get().isLoaded("tacz")){
                    check("native TaCZ starting gun present",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(0).getItem()).toString().equals("tacz:modern_kinetic_gun"));
                    check("default 9mm primary and secondary",ChallengeGuns.ammoId(p.getInventory().getItem(0)).equals("tacz:9mm")&&ChallengeGuns.ammoId(p.getInventory().getItem(1)).equals("tacz:9mm"));
                    Class<?> gunApi=Class.forName("com.tacz.guns.api.item.IGun");Object mainGun=gunApi.getMethod("getIGunOrNull",ItemStack.class).invoke(null,p.getInventory().getItem(0));
                    check("default primary is MP5A5",gunApi.getMethod("getGunId",ItemStack.class).invoke(mainGun,p.getInventory().getItem(0)).toString().equals("tacz:hk_mp5a5"));
                    Object sideGun=gunApi.getMethod("getIGunOrNull",ItemStack.class).invoke(null,p.getInventory().getItem(1));
                    check("default secondary is Glock17",gunApi.getMethod("getGunId",ItemStack.class).invoke(sideGun,p.getInventory().getItem(1)).toString().equals("tacz:glock_17"));
                    check("MP5 starts in automatic mode",gunApi.getMethod("getFireMode",ItemStack.class).invoke(mainGun,p.getInventory().getItem(0)).toString().equals("AUTO"));
                    check("native TaCZ ammunition present",p.getInventory().items.stream().anyMatch(s->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("tacz:ammo")));
                    check("no melee or bow kit",p.getInventory().items.stream().noneMatch(s->s.getItem() instanceof SwordItem||s.is(Items.BOW)||s.is(Items.ARROW)));
                    try{ChallengeLoadout.defaults().select(p,true,2);throw new AssertionError("non-gun accepted");}catch(IllegalArgumentException expected){check("loadout only permits guns",true);}
                    check("AK47 reward model exists",!ChallengeGuns.gun(p,"tacz:ak47").isEmpty());
                }
                room.timer=0;
                var offers=ChallengeShop.offers(p);
                for(var offer:offers)prices.put(offer.title(),offer.cost());
                check("fourteen recipe-priced guns offered",offers.stream().filter(o->!o.gun().isEmpty()).count()==14);
                check("glock recipe sixteen iron plus assembly",offers.stream().anyMatch(o->o.id().equals("glock")&&o.cost()==40));
                check("MP5 recipe iron and lapis plus assembly",offers.stream().anyMatch(o->o.id().equals("mp5")&&o.cost()==105));
            }
            if(ticks==191){var level=server.getLevel(ChallengeArena.DIMENSION);pursuer=(Zombie)level.getEntity(room.mobs.iterator().next());pursuitStart=pursuer.position();}
            if(ticks==200){
                check("first wave runs",room.phase==ChallengeFeature.Phase.RUNNING && room.wave==1);
                check("enemies spawned",!room.mobs.isEmpty());
                var mob=(Zombie)server.getLevel(ChallengeArena.DIMENSION).getEntity(room.mobs.iterator().next());
                check("arena kills do not advance daily tasks",!DailyTasksFeature.active(server).acceptsCombat(p,mob));
                check("configured health applied",mob.getMaxHealth()==20);
                check("slower normal zombies and increased wave count",Math.abs(mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-0.19)<0.001 && room.planned==20);
                check("zombie physically moves along pursuit route",pursuer.position().distanceToSqr(pursuitStart)>0.01);
                check("pursuit has no wandering goal",mob.goalSelector.getAvailableGoals().stream().allMatch(g->g.getGoal() instanceof ChallengePursuitGoal));
                check("spawn assigns room player immediately",mob.getTarget()==p);
                boolean route=mob.getNavigation().moveTo(p,1.15);
                if(!route)System.err.println("route diagnostic mob="+mob.position()+" player="+p.position()+" ground="+mob.onGround()+" path="+mob.getNavigation().createPath(p,0));
                check("pursuit navigation computes route",route);
                check("navigation can reach player through room doors",mob.getNavigation().getPath().canReach());
                balanceBeforeKill=p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore");
                mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(10);
                mob.setAbsorptionAmount(5);check("absorption fixture has real shield health",mob.getAbsorptionAmount()==5);
                mob.hurt(p.damageSources().playerAttack(p),4);
                check("absorbed damage earns no points",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore")==balanceBeforeKill);
                mob.setAbsorptionAmount(0);mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);
                for(int hit=0;hit<3;hit++){mob.invulnerableTime=0;mob.hurt(p.damageSources().playerAttack(p),0.4f);}
                check("fractional health damage accumulates into points",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore")==balanceBeforeKill+1);
                mob.setHealth(mob.getMaxHealth());mob.invulnerableTime=0;
                scoredVictim=mob;mob.hurt(p.damageSources().playerAttack(p),10000);
                if(net.neoforged.fml.ModList.get().isLoaded("tacz")){
                    var cls=Class.forName("com.tacz.guns.api.event.common.EntityKillByGunEvent");
                    var source=p.damageSources().playerAttack(p);
                    var ev=cls.getConstructors()[0].newInstance(p,mob,p,net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"),net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"),10000f,org.apache.commons.lang3.tuple.Pair.of(source,source),true,2f,net.neoforged.fml.LogicalSide.SERVER);
                    NeoForge.EVENT_BUS.post((net.neoforged.bus.api.Event)ev);
                }
            }
            if(ticks==202){
                int expected=(net.neoforged.fml.ModList.get().isLoaded("tacz")?20:10)+20;
                check("confirmed kill awards score with native lethal headshot bonus",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore")==balanceBeforeKill+expected);
                check("combat points are not spendable coins before settlement",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==0);
                check("overkill and healed health do not exceed spawn HP damage budget",Math.abs(p.getPersistentData().getCompound(ChallengeFeature.STATE).getDouble("damageDone")-20)<0.001);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(scoredVictim,p.damageSources().playerAttack(p)));
            }
            if(ticks==204){
                int expected=(net.neoforged.fml.ModList.get().isLoaded("tacz")?20:10)+20;
                check("replayed death cannot award twice",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore")==balanceBeforeKill+expected);
                var level=server.getLevel(ChallengeArena.DIMENSION);
                for(UUID id:room.mobs){var entity=level.getEntity(id);if(entity!=null)entity.discard();}room.mobs.clear();
                room.wave=4;room.phase=ChallengeFeature.Phase.REST;room.timer=0;
            }
            if(ticks==219){var boss=(Zombie)server.getLevel(ChallengeArena.DIMENSION).getEntity(room.boss);boss.setNoAi(true);boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);boss.teleportTo(5.5,65,5.5);stuckPosition=boss.position();room.lastPositions.put(boss.getUUID(),stuckPosition);room.stalled.put(boss.getUUID(),140);}
            if(ticks==220){
                var level=server.getLevel(ChallengeArena.DIMENSION);
                check("boss wave actually spawns boss",room.boss!=null && level.getEntity(room.boss) instanceof Zombie);
                var boss=(Zombie)level.getEntity(room.boss);
                check("stalled zombie repaths without teleporting",room.stalled.get(boss.getUUID())==0 && boss.position().distanceToSqr(stuckPosition)<1);
                check("boss has difficulty and wave scaled health",Math.abs(boss.getMaxHealth()-ChallengeRules.health(room.difficulty,5,true))<0.1);
                check("boss health bar attached",room.bossBar.getPlayers().contains(p));
                check("map signage generated",level.getEntitiesOfClass(Display.TextDisplay.class,new net.minecraft.world.phys.AABB(0,64,0,81,96,81)).size()==ChallengeArena.ROOMS.size()+15+room.arena.sites().size());
                // Native ground navigation must find both flights; no teleport shortcut is accepted.
                var probe=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);probe.setPos(40.5,65,26.5);probe.setOnGround(true);probe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(192);
                for(var waypoint:List.of(room.arena.pos(40,1,11),room.arena.pos(40,11,22),room.arena.pos(40,11,11),room.arena.pos(40,21,22),room.arena.pos(40,21,26))){
                    var route=probe.getNavigation().createPath(waypoint,0);
                    check("physical stair route segment reaches "+waypoint,route!=null&&route.canReach());
                    probe.setPos(waypoint.getX()+0.5,waypoint.getY(),waypoint.getZ()+0.5);probe.setOnGround(true);probe.getNavigation().stop();
                }
                var pad=room.arena.ammoStation(0);
                var originalPosition=p.position();p.setPos(5.5,65,5.5);
                try{feature.resupply(p);throw new AssertionError("remote supply accepted");}catch(IllegalArgumentException expected){check("server rejects remote ammo cabinet request",true);}p.setPos(originalPosition.x,originalPosition.y,originalPosition.z);
                try{feature.resupply(q);throw new AssertionError("non-member supply accepted");}catch(IllegalArgumentException expected){check("server rejects non-participant resupply",true);}
                var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pad,new net.minecraft.world.phys.BlockHitResult(pad.getCenter(),net.minecraft.core.Direction.UP,pad,false));
                Class<?> gunApi=Class.forName("com.tacz.guns.api.item.IGun");Object gunItem=gunApi.getMethod("getIGunOrNull",ItemStack.class).invoke(null,p.getInventory().getItem(0));
                gunApi.getMethod("setCurrentAmmoCount",ItemStack.class,int.class).invoke(gunItem,p.getInventory().getItem(0),0);
                NeoForge.EVENT_BUS.post(click);
                check("real ammo cabinet fills magazine",(int)gunApi.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(gunItem,p.getInventory().getItem(0))==ChallengeGuns.capacity(p.getInventory().getItem(0)));
                var after=p.getInventory().save(new ListTag());
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pad,new net.minecraft.world.phys.BlockHitResult(pad.getCenter(),net.minecraft.core.Direction.UP,pad,false)));
                check("resupply cannot inflate ammo on repeat",p.getInventory().save(new ListTag()).equals(after));
                feature.leave(p,"退出测试");
                check("subthreshold score carries to next settlement",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("pendingScore")==0&&p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("exchangeRemainder")==40);
                check("leave restores original inventory components",p.getInventory().save(new ListTag()).equals(original));
                check("leave restores selected slot",p.getInventory().selected==4);
                check("leave restores survival",p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL);
                check("leave restores dimension",p.level()==server.overworld());
                check("return marker cleared",!ChallengeInventory.pending(p));
                check("room removed",feature.rooms().isEmpty());
                check("arena mob removed",boss.isRemoved());
                ChallengeInventory.enter(p);NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
                check("reconnect recovers saved inventory",p.getInventory().save(new ListTag()).equals(original)&&!ChallengeInventory.pending(p));
                if(net.neoforged.fml.ModList.get().isLoaded("tacz")){
                    ItemStack own=ChallengeGuns.gun(p,"tacz:ak47");own.set(DataComponents.CUSTOM_NAME,Component.literal("自带改装 AK"));
                    Class<?> api=Class.forName("com.tacz.guns.api.item.IGun");Object gun=api.getMethod("getIGunOrNull",ItemStack.class).invoke(null,own);
                    Class<?> attachment=Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");Object scope=Enum.valueOf((Class<Enum>)attachment,"SCOPE");
                    Class<?> builderType=Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
                    for(String attachmentId:List.of("tacz:scope_acog_ta31","tacz:extended_mag_3")){
                        Object builder=builderType.getMethod("create").invoke(null);builderType.getMethod("setId",net.minecraft.resources.ResourceLocation.class).invoke(builder,net.minecraft.resources.ResourceLocation.parse(attachmentId));
                        ItemStack attachmentStack=(ItemStack)builderType.getMethod("build").invoke(builder);
                        api.getMethod("installAttachment",net.minecraft.core.HolderLookup.Provider.class,ItemStack.class,ItemStack.class).invoke(gun,p.registryAccess(),own,attachmentStack);
                    }
                    check("extended magazine fixture increases capacity",ChallengeGuns.capacity(own)>30);
                    p.getInventory().setItem(5,own);var loadout=ChallengeLoadout.defaults().select(p,true,5);
                    try{loadout.select(p,false,5);throw new AssertionError("same gun twice accepted");}catch(IllegalArgumentException expected){check("same slot primary and secondary rejected",true);}
                    own.set(DataComponents.CUSTOM_NAME,Component.literal("改装发生改变"));
                    try{loadout.validate(p);throw new AssertionError("stale loadout accepted");}catch(IllegalArgumentException expected){check("changed selected components require reselection",true);}
                    loadout=ChallengeLoadout.defaults().select(p,true,5);ItemStack expected=own.copy();
                    ChallengeInventory.enter(p,loadout);
                    check("selected gun retains native scope ID",api.getMethod("getAttachmentId",ItemStack.class,attachment).invoke(gun,p.getInventory().getItem(0),scope).toString().equals("tacz:scope_acog_ta31"));
                    check("refill respects extended magazine",(int)api.getMethod("getCurrentAmmoCount",ItemStack.class).invoke(gun,p.getInventory().getItem(0))==ChallengeGuns.capacity(expected));
                    check("resupply resolves two selected calibers",!ChallengeGuns.ammoId(p.getInventory().getItem(0)).equals("tacz:9mm") && ChallengeGuns.supplies(p,60).size()==2);
                    ChallengeInventory.restore(p);check("customized original weapon restored exactly",ItemStack.matches(expected,p.getInventory().getItem(5)));
                    p.getInventory().load(original);
                }
                room=feature.create(p,ChallengeRules.Difficulty.HARD);
            }
            if(ticks==350){
                feature.start(p);room.phase=ChallengeFeature.Phase.RUNNING;room.wave=10;room.issued=room.planned=0;room.kills.put(p.getUUID(),100);room.timer=server.getTickCount()+1000;
            }
            if(ticks==352){
                check("final wave completion closes room",feature.rooms().isEmpty());
                CompoundTag t=p.getPersistentData().getCompound(ChallengeFeature.STATE);
                check("win recorded once",t.getInt("wins")==1&&t.getInt("hardWins")==1);
                check("completion credits replace automatic loot",t.getInt("credits")>0 && t.getList("rewards",Tag.TAG_COMPOUND).isEmpty());
                int base=t.getInt("credits");feature.claimTask(p,0);
                int balance=p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits");check("milestone awards shop credits",balance==base+4);
                try{feature.claimTask(p,0);throw new AssertionError("duplicate task accepted");}catch(IllegalArgumentException expected){check("duplicate task rejected",true);}
                for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                try{feature.buy(p,"diamond",0);throw new AssertionError("full inventory purchase accepted");}catch(IllegalArgumentException expected){check("full inventory does not debit wallet",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balance);}
                check("full inventory rolls back partial insertions",p.getInventory().items.stream().allMatch(s->s.is(Items.COBBLESTONE)&&s.getCount()==64));
                p.getInventory().load(original);feature.buy(p,"diamond",0);
                check("shop debits exact server price",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balance-40);
                check("actual diamond reward granted",p.getInventory().items.stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum()>13);
                try{feature.buy(p,"diamond",0);throw new AssertionError("duplicate order accepted");}catch(IllegalArgumentException expected){check("duplicate shop order rejected",true);}
                try{feature.buy(p,"ak47",1);throw new AssertionError("insufficient funds accepted");}catch(IllegalArgumentException expected){check("insufficient funds rejected",true);}
                check("daily tasks still active",DailyTasksFeature.active(server)!=null);
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(q,p,false));
                check("challenge wins survive death clone",q.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("wins")==1);
                room=feature.create(p,ChallengeRules.Difficulty.NORMAL);
            }
            if(ticks==490){
                feature.start(p);room.phase=ChallengeFeature.Phase.RUNNING;room.timer=server.getTickCount()+1000;
                LivingDeathEvent death=new LivingDeathEvent(p,p.damageSources().generic());NeoForge.EVENT_BUS.post(death);
                check("player death canceled before grave/drop path",death.isCanceled());
                check("defeated member removed from surviving team",!room.alive.contains(p.getUUID()));
            }
            if(ticks==492){
                check("defeat restores player and closes solo room",!ChallengeInventory.pending(p)&&p.level()==server.overworld()&&feature.rooms().isEmpty());
                room=feature.create(p,ChallengeRules.Difficulty.NORMAL);room.arena.build(server.getLevel(ChallengeArena.DIMENSION),300000);
            }
            if(ticks==495){
                feature.start(p);feature.clientReady(p);room.phase=ChallengeFeature.Phase.RUNNING;room.wave=1;room.planned=1;room.issued=0;room.timer=server.getTickCount()+2000;
                var level=server.getLevel(ChallengeArena.DIMENSION);p.teleportTo(level,40.5,85,26.5,0,0);
                var spawn=ChallengeFeature.class.getDeclaredMethod("spawn",ChallengeFeature.Room.class,ServerLevel.class);spawn.setAccessible(true);spawn.invoke(feature,room,level);
                walkerId=room.mobs.iterator().next();
            }
            if(ticks>495&&stairWalker==null&&walkerId!=null){var entity=server.getLevel(ChallengeArena.DIMENSION).getEntity(walkerId);if(entity instanceof Zombie z){stairWalker=z;stairWalker.teleportTo(40.5,65,26.5);stairWalker.setOnGround(true);}}
            if(ticks>495&&stairWalker!=null&&stairWalker.getY()>=84.5&&stairWalker.distanceToSqr(p)<9){check("live zombie physically climbs two stair flights to the player",true);feature.leave(p,"route complete");finish(server,null);}
            if(ticks>495&&ticks%100==0&&stairWalker!=null){var path=stairWalker.getNavigation().getPath();System.err.println("STAIR WALK tick="+ticks+" pos="+stairWalker.position()+" target="+p.position()+" pathEnd="+(path==null?"none":path.getEndNode())+" velocity="+stairWalker.getDeltaMovement());}
            if(ticks>1750)throw new AssertionError("stair walk timeout, position="+(stairWalker==null?"none":stairWalker.position()));
        }catch(Throwable error){finish(server,error);}
    }
}
