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
        try{var result=new LinkedHashMap<String,Object>();result.put("success",error==null);result.put("passed",passed);if(error!=null){result.put("error",error.toString());error.printStackTrace();}Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){e.printStackTrace();}server.halt(false);
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
                check("mob count capped",ChallengeRules.count(500,100)==48);
                p=player(server,"ChallengeQA");q=player(server,"ChallengeGuest");
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
            if(ticks==180){
                check("incremental map completes",room.phase==ChallengeFeature.Phase.LOBBY);
                var level=server.getLevel(ChallengeArena.DIMENSION);
                for(int cx=0;cx<=3;cx++)for(int cz=0;cz<=3;cz++)level.setChunkForced(cx,cz,true);
                check("floor at spawn is solid",level.getBlockState(room.arena.spawn().below()).isSolidRender(level,room.arena.spawn().below()));
                check("spawn has two blocks headroom",level.isEmptyBlock(room.arena.spawn())&&level.isEmptyBlock(room.arena.spawn().above()));
                for(int floor=0;floor<3;floor++)for(var s:room.arena.spawns(floor))check("safe zombie spawn "+s,level.isEmptyBlock(s)&&level.isEmptyBlock(s.above()));
                check("map has twelve rooms",ChallengeArena.ROOMS.size()==12);
                check("lift reaches next floor",room.arena.floor(room.arena.lift(0,true).getY())==1);
                check("lift wraps from roof",room.arena.floor(room.arena.lift(2,true).getY())==0);
                feature.start(p);
                check("solo start uses countdown",room.phase==ChallengeFeature.Phase.COUNTDOWN && room.alive.size()==1);
                check("player enters isolated dimension",ChallengeFeature.dimension(p.level()));
                check("original inventory persisted",ChallengeInventory.pending(p));
                check("original diamonds not in kit",p.getInventory().items.stream().noneMatch(s->s.is(Items.DIAMOND)));
                check("adventure mode active",p.gameMode.getGameModeForPlayer()==GameType.ADVENTURE);
                if(net.neoforged.fml.ModList.get().isLoaded("tacz")){
                    check("native TaCZ starting gun present",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getInventory().getItem(0).getItem()).toString().equals("tacz:modern_kinetic_gun"));
                    check("native TaCZ ammunition present",p.getInventory().getItem(10).getCount()==60);
                    check("AK47 reward model exists",!ChallengeGuns.gun(p,"tacz:ak47").isEmpty());
                }
                room.timer=0;
            }
            if(ticks==191){var level=server.getLevel(ChallengeArena.DIMENSION);pursuer=(Zombie)level.getEntity(room.mobs.iterator().next());pursuitStart=pursuer.position();}
            if(ticks==200){
                check("first wave runs",room.phase==ChallengeFeature.Phase.RUNNING && room.wave==1);
                check("enemies spawned",!room.mobs.isEmpty());
                var mob=(Zombie)server.getLevel(ChallengeArena.DIMENSION).getEntity(room.mobs.iterator().next());
                check("arena kills do not advance daily tasks",!DailyTasksFeature.active(server).acceptsCombat(p,mob));
                check("configured health applied",mob.getMaxHealth()==20);
                check("zombie physically moves along pursuit route",pursuer.position().distanceToSqr(pursuitStart)>0.01);
                check("pursuit has no wandering goal",mob.goalSelector.getAvailableGoals().stream().allMatch(g->g.getGoal() instanceof ChallengePursuitGoal));
                check("spawn assigns room player immediately",mob.getTarget()==p);
                boolean route=mob.getNavigation().moveTo(p,1.15);
                if(!route)System.err.println("route diagnostic mob="+mob.position()+" player="+p.position()+" ground="+mob.onGround()+" path="+mob.getNavigation().createPath(p,0));
                check("pursuit navigation computes route",route);
                check("navigation can reach player through room doors",mob.getNavigation().getPath().canReach());
                balanceBeforeKill=p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits");
                scoredVictim=mob;mob.hurt(p.damageSources().playerAttack(p),10000);
                if(net.neoforged.fml.ModList.get().isLoaded("tacz")){
                    var cls=Class.forName("com.tacz.guns.api.event.common.EntityKillByGunEvent");
                    var source=p.damageSources().playerAttack(p);
                    var ev=cls.getConstructors()[0].newInstance(p,mob,p,net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"),net.minecraft.resources.ResourceLocation.parse("tacz:glock_17"),10000f,org.apache.commons.lang3.tuple.Pair.of(source,source),true,2f,net.neoforged.fml.LogicalSide.SERVER);
                    NeoForge.EVENT_BUS.post((net.neoforged.bus.api.Event)ev);
                }
            }
            if(ticks==202){
                int expected=net.neoforged.fml.ModList.get().isLoaded("tacz")?20:10;
                check("confirmed kill awards score with native lethal headshot bonus",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balanceBeforeKill+expected);
                NeoForge.EVENT_BUS.post(new LivingDeathEvent(scoredVictim,p.damageSources().playerAttack(p)));
            }
            if(ticks==204){
                int expected=net.neoforged.fml.ModList.get().isLoaded("tacz")?20:10;
                check("replayed death cannot award twice",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balanceBeforeKill+expected);
                var level=server.getLevel(ChallengeArena.DIMENSION);
                for(UUID id:room.mobs){var entity=level.getEntity(id);if(entity!=null)entity.discard();}room.mobs.clear();
                room.wave=4;room.phase=ChallengeFeature.Phase.REST;room.timer=0;
            }
            if(ticks==219){var boss=(Zombie)server.getLevel(ChallengeArena.DIMENSION).getEntity(room.boss);boss.setNoAi(true);boss.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);boss.teleportTo(5.5,65,5.5);stuckPosition=boss.position();room.lastPositions.put(boss.getUUID(),stuckPosition);room.stalled.put(boss.getUUID(),140);}
            if(ticks==220){
                var level=server.getLevel(ChallengeArena.DIMENSION);
                check("boss wave actually spawns boss",room.boss!=null && level.getEntity(room.boss) instanceof Zombie);
                var boss=(Zombie)level.getEntity(room.boss);
                check("stalled zombie relocates and resumes pursuit",room.stalled.get(boss.getUUID())==0 && boss.position().distanceToSqr(stuckPosition)>1 && boss.distanceToSqr(p)>=64);
                check("boss has difficulty and wave scaled health",Math.abs(boss.getMaxHealth()-ChallengeRules.health(room.difficulty,5,true))<0.1);
                check("boss health bar attached",room.bossBar.getPlayers().contains(p));
                check("map signage generated",level.getEntitiesOfClass(Display.TextDisplay.class,new net.minecraft.world.phys.AABB(0,64,0,49,90,49)).size()==27);
                var pad=room.arena.pos(24,0,20);
                var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pad,new net.minecraft.world.phys.BlockHitResult(pad.getCenter(),net.minecraft.core.Direction.UP,pad,false));
                int before=p.getInventory().items.stream().filter(s->s.is(Items.ARROW)).mapToInt(ItemStack::getCount).sum();
                NeoForge.EVENT_BUS.post(click);
                int after=p.getInventory().items.stream().filter(s->s.is(Items.ARROW)).mapToInt(ItemStack::getCount).sum();
                check("real ammo pad grants replenishment",after==before+32);
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pad,new net.minecraft.world.phys.BlockHitResult(pad.getCenter(),net.minecraft.core.Direction.UP,pad,false)));
                check("resupply cooldown blocks immediate repeat",p.getInventory().items.stream().filter(s->s.is(Items.ARROW)).mapToInt(ItemStack::getCount).sum()==after);
                feature.leave(p,"退出测试");
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
                    CompoundTag attachmentData=new CompoundTag();attachmentData.putString("id","tacz:attachment");attachmentData.putInt("count",1);
                    api.getMethod("setAttachmentTag",ItemStack.class,attachment,CompoundTag.class).invoke(gun,own,scope,attachmentData);
                    p.getInventory().setItem(5,own);var loadout=ChallengeLoadout.defaults().select(p,true,5);
                    try{loadout.select(p,false,5);throw new AssertionError("same gun twice accepted");}catch(IllegalArgumentException expected){check("same slot primary and secondary rejected",true);}
                    own.set(DataComponents.CUSTOM_NAME,Component.literal("改装发生改变"));
                    try{loadout.validate(p);throw new AssertionError("stale loadout accepted");}catch(IllegalArgumentException expected){check("changed selected components require reselection",true);}
                    loadout=ChallengeLoadout.defaults().select(p,true,5);ItemStack expected=own.copy();
                    ChallengeInventory.enter(p,loadout);
                    check("selected gun retains every attachment and component",ItemStack.matches(expected,p.getInventory().getItem(0)));
                    check("resupply resolves selected gun ammo caliber",!ChallengeGuns.ammoId(p.getInventory().getItem(0)).equals("tacz:9mm") && ChallengeGuns.supplies(p,60).size()==1);
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
                int balance=p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits");check("milestone awards shop credits",balance==base+350);
                try{feature.claimTask(p,0);throw new AssertionError("duplicate task accepted");}catch(IllegalArgumentException expected){check("duplicate task rejected",true);}
                for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                try{feature.buy(p,"diamond",0);throw new AssertionError("full inventory purchase accepted");}catch(IllegalArgumentException expected){check("full inventory does not debit wallet",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balance);}
                check("full inventory rolls back partial insertions",p.getInventory().items.stream().allMatch(s->s.is(Items.COBBLESTONE)&&s.getCount()==64));
                p.getInventory().load(original);feature.buy(p,"diamond",0);
                check("shop debits exact server price",p.getPersistentData().getCompound(ChallengeFeature.STATE).getInt("credits")==balance-400);
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
            if(ticks==492){check("defeat restores player and closes solo room",!ChallengeInventory.pending(p)&&p.level()==server.overworld()&&feature.rooms().isEmpty());finish(server,null);}
            if(ticks>550)throw new AssertionError("test timeout");
        }catch(Throwable error){finish(server,error);}
    }
}
