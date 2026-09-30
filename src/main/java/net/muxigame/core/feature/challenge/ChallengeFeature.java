package net.muxigame.core.feature.challenge;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.tasks.TaskOwnership;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Server-thread-only room state machine. No production commands, reloads or restart operations. */
public final class ChallengeFeature implements ServerFeature {
    public static final String MOB="muxi_challenge_room", STATE="muxi_challenge_progress";
    private static final Map<MinecraftServer,ChallengeFeature> ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
    public enum Phase { BUILDING, LOBBY, LOADING, COUNTDOWN, RUNNING, REST }
    public static final class Room {
        public final String id=UUID.randomUUID().toString().substring(0,8);
        public final UUID host;
        public final ChallengeArena arena;
        public final LinkedHashSet<UUID> members=new LinkedHashSet<>();
        public final Set<UUID> alive=new HashSet<>();
        public final Set<UUID> ready=new HashSet<>();
        public List<ChallengeArena.SpawnSite> activeSites=List.of();
        public int preparedWave;
        public boolean flash;
        public final Map<UUID,Long> invites=new HashMap<>();
        public final Map<UUID,Integer> kills=new HashMap<>();
        public final Map<UUID,Integer> earned=new HashMap<>();
        public final Map<UUID,Double> damageRemaining=new HashMap<>();
        public final Map<UUID,net.minecraft.world.phys.Vec3> lastPositions=new HashMap<>();
        public final Map<UUID,Integer> stalled=new HashMap<>();
        public final Set<UUID> mobs=new HashSet<>();
        /** Only the latest small batch; mobs retains survivors from every batch in the big wave. */
        public final Set<UUID> batchMobs=new HashSet<>();
        public final Map<String,Integer> supplies=new HashMap<>();
        public ChallengeRules.Difficulty difficulty;
        public Phase phase=Phase.BUILDING;
        public int wave,timer,started,created,issued,planned;
        public int batchNumber,batchCount,batchSize,batchLimit,batchDeadline;
        public final net.minecraft.server.level.ServerBossEvent bossBar=new net.minecraft.server.level.ServerBossEvent(Component.literal("感染暴君"),net.minecraft.world.BossEvent.BossBarColor.PURPLE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        public UUID boss;
        public final Map<UUID,ChallengeRules.Enemy> bosses=new LinkedHashMap<>();
        public final Set<UUID> reinforcements=new HashSet<>();
        public int reinforcementIssued;
        public final Map<UUID,Integer> wallet=new HashMap<>(),orders=new HashMap<>();
        public final Map<UUID,AmmoHold> holds=new HashMap<>();
        public int batchStarted,batchDuration,batchQuota;
        public double tempo=1;
        Room(UUID host,int slot,ChallengeRules.Difficulty d,int now) {this.host=host;arena=new ChallengeArena(slot);difficulty=d;members.add(host);created=now;}
    }
    public static final class AmmoHold {public final int started;public int heartbeat;AmmoHold(int tick){started=heartbeat=tick;}}
    private MinecraftServer server;
    private int ticks;
    private final List<Room> rooms=new ArrayList<>();
    private final Map<UUID,Integer> rate=new HashMap<>();
    private final Map<UUID,Integer> readRate=new HashMap<>();
    private final Map<UUID,LivingDeathEvent> deaths=new HashMap<>();
    private final Set<UUID> evacuate=new HashSet<>();
    private record Headshot(UUID player,int tick){}
    private final Map<UUID,Headshot> headshots=new HashMap<>();
    private final Map<UUID,ChallengeLoadout> loadouts=new HashMap<>();
    private final Set<UUID> dirty=new HashSet<>();
    private record DamageSample(net.minecraft.world.damagesource.DamageSource source,float health,int tick){}
    private final Map<LivingEntity,ArrayDeque<DamageSample>> damageSamples=new WeakHashMap<>();
    public static ChallengeFeature active(MinecraftServer server) {return ACTIVE.get(server);}
    public static boolean locked(ServerPlayer p) {return ChallengeInventory.pending(p);}
    public static boolean dimension(net.minecraft.world.level.Level level) {return level.dimension().equals(ChallengeArena.DIMENSION);}
    public Room room(UUID player) {return rooms.stream().filter(r->r.members.contains(player)).findFirst().orElse(null);}
    public List<Room> rooms() {return List.copyOf(rooms);}
    public String id(){return "zombie-challenge";}
    public void register(IEventBus bus) {
        bus.addListener(this::started);bus.addListener(this::stopping);bus.addListener(this::tick);
        bus.addListener(this::login);bus.addListener(this::logout);bus.addListener(this::commands);
        bus.addListener(this::clonePlayer);bus.addListener(this::respawn);
        bus.addListener(EventPriority.HIGHEST,this::death);bus.addListener(EventPriority.LOWEST,true,this::trackDeath);
        bus.addListener(this::damage);bus.addListener(this::drops);bus.addListener(this::xp);
        bus.addListener(EventPriority.HIGHEST,this::beforeDamage);bus.addListener(EventPriority.LOWEST,this::afterDamage);
        bus.addListener(this::join);bus.addListener(this::interact);bus.addListener(this::breakBlock);
        bus.addListener(this::place);bus.addListener(this::explosion);bus.addListener(this::toss);bus.addListener(this::command);
        bus.addListener(this::entityInteract);
        bus.addListener(this::travel);
        bus.addListener((net.neoforged.neoforge.event.OnDatapackSyncEvent e)->ChallengeShop.invalidate());
        if(ModList.get().isLoaded("tacz"))new ChallengeHeadshots(this,bus);
    }
    private void started(ServerStartedEvent e) {server=e.getServer();ACTIVE.put(server,this);ChallengeShop.invalidate();ServerLevel level=server.getLevel(ChallengeArena.DIMENSION);if(level!=null)for(int i=0;i<ChallengeRules.MAX_ROOMS;i++)new ChallengeArena(i).keepLoaded(level,false);}
    private void stopping(ServerStoppingEvent e) {if(e.getServer()==server){for(Room r:List.copyOf(rooms)) finish(r,false,"服务器维护，本局不结算通关奖励");close();}}
    public void close(){if(server!=null)ACTIVE.remove(server);rooms.clear();deaths.clear();rate.clear();readRate.clear();evacuate.clear();headshots.clear();loadouts.clear();dirty.clear();damageSamples.clear();server=null;}
    private void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && p.server==server) {
            if(locked(p)){ChallengeInventory.restore(p);tell(p,"已恢复挑战前的背包和位置；中断的挑战不重复结算。");}
            exchangePending(p);
            if(dimension(p.level())) {BlockPos s=server.overworld().getSharedSpawnPos();p.teleportTo(server.overworld(),s.getX()+0.5,s.getY()+1,s.getZ()+0.5,0,0);}
            send(p,"");
        }
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && p.server==server){leave(p,"玩家离线");rate.remove(p.getUUID());readRate.remove(p.getUUID());loadouts.remove(p.getUUID());dirty.remove(p.getUUID());}
    }
    private void clonePlayer(PlayerEvent.Clone e){
        if(e.getEntity().level().isClientSide())return;
        for(String key:List.of(STATE,ChallengeInventory.KEY))if(e.getOriginal().getPersistentData().contains(key))
            e.getEntity().getPersistentData().put(key,e.getOriginal().getPersistentData().get(key).copy());
    }
    private void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p && p.server==server && locked(p))leave(p,"挑战中断，恢复背包");}
    private void tell(ServerPlayer p,String text){p.sendSystemMessage(Component.literal("[僵尸挑战] "+text));}
    private void notice(Room r,String text){for(UUID id:r.members){var p=server.getPlayerList().getPlayer(id);if(p!=null)tell(p,text);}}
    private void require(boolean pass,String message){if(!pass)throw new IllegalArgumentException(message);}
    public void handle(ServerPlayer p,String action,String value) {
        if(p.server!=server || !server.isSameThread())return;
        int now=server.getTickCount();
        if(action.equals("ammoCancel")){Room r=room(p.getUUID());if(r!=null)r.holds.remove(p.getUUID());return;}
        if(action.equals("ammoFinish")){Room r=room(p.getUUID());AmmoHold hold=r==null?null:r.holds.get(p.getUUID());if(hold==null||now-hold.started<ChallengeRules.AMMO_HOLD)return;try{resupply(p);}catch(IllegalArgumentException ignored){r.holds.remove(p.getUUID());}return;}
        if(action.equals("ready")){clientReady(p);return;}
        if(action.equals("list")){if(now-readRate.getOrDefault(p.getUUID(),-100)<10)return;readRate.put(p.getUUID(),now);send(p,"");return;}
        if(now-rate.getOrDefault(p.getUUID(),-100)<4)return;
        rate.put(p.getUUID(),now);
        try {
            switch(action) {
                case "list" -> {send(p,"");return;}
                case "create" -> create(p,ChallengeRules.Difficulty.parse(value));
                case "join" -> joinRoom(p,value);
                case "invite" -> invite(p,UUID.fromString(value));
                case "start" -> start(p);
                case "ready" -> {clientReady(p);send(p,"");return;}
                case "resupply", "ammoHold" -> {ammoHold(p);return;}
                case "battleBuy" -> {String[] parts=value.split(":",4);require(parts.length==4,"无效战术订单");battleBuy(p,parts[0],Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),parts[3].equals("coin"));}
                case "leave" -> leave(p,"主动退出");
                case "difficulty" -> {Room r=host(p);require(r.phase==Phase.LOBBY,"开局后不能更改难度");r.difficulty=ChallengeRules.Difficulty.parse(value);}
                case "claim" -> claim(p);
                case "task" -> claimTask(p,Integer.parseInt(value));
                case "buy" -> {String[] parts=value.split(":",3);require(parts.length==3,"请更新客户端后使用商店");int quoted=Integer.parseInt(parts[2]);require(ChallengeShop.offers(p).stream().anyMatch(o->o.id().equals(parts[0])&&o.cost()==quoted),"商品价格已变化，请刷新商店");buy(p,parts[0],Integer.parseInt(parts[1]));}
                case "primary", "primary2", "secondary" -> selectWeapon(p,action.equals("primary")?0:action.equals("primary2")?1:2,Integer.parseInt(value));
                default -> throw new IllegalArgumentException("未知挑战操作");
            }
            send(p,"操作成功");
        } catch(IllegalArgumentException e){send(p,e.getMessage()==null?"操作无效":e.getMessage());}
        catch(Exception e){org.slf4j.LoggerFactory.getLogger("muxi-game-core/challenge").error("Challenge action failed: {}",action,e);send(p,"操作未完成，请联系管理员；恢复记录已保留");}
    }
    private Room host(ServerPlayer p){Room r=room(p.getUUID());require(r!=null && r.host.equals(p.getUUID()),"只有房主可以操作");return r;}
    public Room create(ServerPlayer p,ChallengeRules.Difficulty d) {
        require(room(p.getUUID())==null && !locked(p),"请先离开当前房间");
        require(server.getLevel(ChallengeArena.DIMENSION)!=null,"挑战维度尚未加载，需要服务端更新后启动");
        require(rooms.size()<ChallengeRules.MAX_ROOMS,"当前房间已满，请稍后再试");
        int slot=0;while(slot<ChallengeRules.MAX_ROOMS){int n=slot;if(rooms.stream().noneMatch(r->r.arena.origin()==n*ChallengeArena.SPACING))break;slot++;}
        Room r=new Room(p.getUUID(),slot,d,server.getTickCount());rooms.add(r);
        ServerLevel level=server.getLevel(ChallengeArena.DIMENSION);
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(r.arena.origin(),0,0,r.arena.origin()+ChallengeArena.SIZE,256,ChallengeArena.SIZE)))if(!(entity instanceof ServerPlayer))entity.discard();
        return r;
    }
    private void joinRoom(ServerPlayer p,String id){
        require(room(p.getUUID())==null && !locked(p),"请先离开当前房间");
        Room r=rooms.stream().filter(it->it.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("房间已结束"));
        require(r.phase==Phase.LOBBY || r.phase==Phase.BUILDING,"该房间已经开局");
        require(r.invites.getOrDefault(p.getUUID(),0L)>=server.getTickCount(),"需要房主邀请，邀请有效期五分钟");
        require(r.members.size()<ChallengeRules.MAX_PLAYERS,"房间人数已满");r.members.add(p.getUUID());r.invites.remove(p.getUUID());notice(r,p.getDisplayName().getString()+" 加入房间");
    }
    private void invite(ServerPlayer p,UUID target){
        Room r=host(p);require(r.phase==Phase.LOBBY || r.phase==Phase.BUILDING,"已经开局");
        ServerPlayer to=server.getPlayerList().getPlayer(target);require(to!=null && to!=p,"请选择在线玩家");
        require(room(target)==null && !locked(to),"对方已经在房间中");
        r.invites.put(target,(long)server.getTickCount()+6000);
        to.sendSystemMessage(Component.literal("[僵尸挑战] "+p.getDisplayName().getString()+" 邀请你挑战 · "+r.difficulty.title+" [点击加入]")
            .withStyle(s->s.withColor(0x8AF0A8).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/muxichallenge join "+r.id))));send(to,"收到挑战邀请："+r.id);
    }
    public void start(ServerPlayer p) {
        Room r=host(p);require(r.phase==Phase.LOBBY,"地图还在生成或游戏已开始");
        for(UUID id:r.members){ServerPlayer member=server.getPlayerList().getPlayer(id);if(member!=null)require(progress(member).getList("rewards",Tag.TAG_COMPOUND).size()<=112,"有成员的待领挑战奖励已满，请先领取");}
        for(UUID id:r.members){ServerPlayer member=server.getPlayerList().getPlayer(id);if(member!=null)loadouts.getOrDefault(id,ChallengeLoadout.defaults()).validate(member);}
        for(UUID id:r.members){ServerPlayer member=server.getPlayerList().getPlayer(id);require(member!=null,"有成员离线");require(!locked(member),"成员还有未恢复的背包");require(member.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"成员必须处于生存模式");require(member.containerMenu==member.inventoryMenu && member.containerMenu.getCarried().isEmpty(),"所有成员请先关闭容器、放下手持物品");require(!dimension(member.level()) && !member.isPassenger(),"成员当前无法传送");}
        Map<UUID,Integer> fees=new LinkedHashMap<>();
        Set<UUID> charged=new HashSet<>();
        for(UUID id:r.members){var member=server.getPlayerList().getPlayer(id);int fee=loadouts.getOrDefault(id,ChallengeLoadout.defaults()).fee(member);require(progress(member).getInt("credits")>=fee,"有成员的兑换币不足以携带自选武器");fees.put(id,fee);}
        try {
            r.arena.keepLoaded(server.getLevel(ChallengeArena.DIMENSION),true);
            for(UUID id:r.members){ServerPlayer member=server.getPlayerList().getPlayer(id);ChallengeInventory.enter(member,loadouts.getOrDefault(id,ChallengeLoadout.defaults()));BlockPos s=r.arena.spawn();member.teleportTo(server.getLevel(ChallengeArena.DIMENSION),s.getX()+0.5,s.getY(),s.getZ()+0.5,0,0);r.alive.add(id);r.wallet.put(id,300);}
            for(UUID id:r.members){if(fees.get(id)==0)continue;var member=server.getPlayerList().getPlayer(id);CompoundTag t=progress(member);t.putInt("credits",t.getInt("credits")-fees.get(id));t.putInt("shopRevision",t.getInt("shopRevision")+1);charged.add(id);store(member,t);}
            r.phase=Phase.LOADING;r.timer=server.getTickCount()+2400;r.started=server.getTickCount();
            notice(r,"等待队员加载地图，全部就绪后开始 30 秒首波倒计时。楼梯上下楼；弹药柜附近按换弹键补满弹药。");
        }catch(RuntimeException e){for(UUID id:charged){var member=server.getPlayerList().getPlayer(id);if(member!=null){var t=progress(member);t.putInt("credits",t.getInt("credits")+fees.get(id));store(member,t);}}finish(r,false,"开局失败，恢复背包");throw e;}
    }
    public void leave(ServerPlayer p,String reason){
        Room r=room(p.getUUID());
        if(locked(p))ChallengeInventory.restore(p);
        exchangePending(p);
        if(r==null)return;
        r.members.remove(p.getUUID());r.alive.remove(p.getUUID());
        r.holds.remove(p.getUUID());
        r.bossBar.removePlayer(p);
        if(r.members.isEmpty() || (r.host.equals(p.getUUID()) && (r.phase==Phase.LOBBY || r.phase==Phase.BUILDING)))finish(r,false,reason);
        else notice(r,p.getDisplayName().getString()+" 已离场："+reason);
    }
    public void clientReady(ServerPlayer p){Room r=room(p.getUUID());if(r==null||r.phase!=Phase.LOADING||!locked(p)||!dimension(p.level())||!r.alive.contains(p.getUUID()))return;r.ready.add(p.getUUID());if(r.ready.containsAll(r.alive)){r.phase=Phase.COUNTDOWN;r.timer=server.getTickCount()+600;planWave(r,1);notice(r,"地图加载完成，首波将在 30 秒后开始。红色爆闪灯标示已激活刷怪口，可提前布防。");}}
    public void resupply(ServerPlayer p){
        Room r=room(p.getUUID());AmmoHold hold=r==null?null:r.holds.get(p.getUUID());require(hold!=null&&server.getTickCount()-hold.started>=ChallengeRules.AMMO_HOLD&&server.getTickCount()-hold.heartbeat<=8,"请在补给点持续按住换弹键0.5秒，松开取消");
        checkSupply(p);
        ChallengeGuns.refill(p);r.supplies.put(p.getUUID()+":ammo",server.getTickCount());r.holds.remove(p.getUUID());send(p,"弹药补满");
    }
    private Room checkSupply(ServerPlayer p){
        Room r=room(p.getUUID());require(r!=null&&locked(p)&&r.alive.contains(p.getUUID())&&dimension(p.level()),"只可在挑战中补弹");
        BlockPos station=r.arena.ammoStation(0);
        require(p.distanceToSqr(station.getCenter())<=25&&p.level().getBlockState(station).is(net.minecraft.world.level.block.Blocks.BARREL),"请靠近弹药补给柜（5 格内）");
        String key=p.getUUID()+":ammo";int now=server.getTickCount();require(now-r.supplies.getOrDefault(key,-10000)>=ChallengeRules.AMMO_COOLDOWN,"弹药柜冷却 10 秒；仍可正常换弹");
        return r;
    }
    public void ammoHold(ServerPlayer p){Room r=checkSupply(p);AmmoHold hold=r.holds.computeIfAbsent(p.getUUID(),id->new AmmoHold(server.getTickCount()));hold.heartbeat=server.getTickCount();}
    public void selectWeapon(ServerPlayer p,int role,int slot){
        Room r=room(p.getUUID());require(!locked(p)&&(r==null||r.phase==Phase.LOBBY||r.phase==Phase.BUILDING),"仅准备阶段可选择武器");loadouts.put(p.getUUID(),loadouts.getOrDefault(p.getUUID(),ChallengeLoadout.defaults()).select(p,role,slot));
    }
    public void battleBuy(ServerPlayer p,String id,int slot,int revision,boolean coins){
        Room r=room(p.getUUID());require(r!=null&&locked(p)&&r.alive.contains(p.getUUID())&&r.phase!=Phase.LOADING,"只可在挑战中使用战术轮盘");
        require(revision==r.orders.getOrDefault(p.getUUID(),0),"订单已处理，请刷新轮盘");
        var offer=ChallengeBattleShop.offers(p).stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("商品当前不可用"));
        CompoundTag t=progress(p);int price=coins?offer.coins():offer.cost();require(!coins||price>0,"枪械和护甲只用战术点购买");
        require((coins?t.getInt("credits"):r.wallet.getOrDefault(p.getUUID(),0))>=price,"余额不足");
        ItemStack stack=offer.stack(p);require(!stack.isEmpty(),"商品不存在");ListTag before=p.getInventory().save(new ListTag());
        if(!offer.gun().isEmpty()){
            require(slot>=0&&slot<3,"请选择三枪槽之一");require((slot==2)==ChallengeGuns.pistol(stack),"手枪仅替换副武器，其他枪械替换主武器");
            require(!ChallengeGuns.gunId(p.getInventory().getItem(slot)).equals(offer.gun()),"该槽已有同款枪械");
            com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(p).cancelReload();ChallengeGuns.fillMagazine(stack);p.getInventory().setItem(slot,stack);
        }else if(offer.category().equals("护甲")){
            Item[] armor=switch(id){case "iron_armor"->new Item[]{Items.IRON_BOOTS,Items.IRON_LEGGINGS,Items.IRON_CHESTPLATE,Items.IRON_HELMET};case "diamond_armor"->new Item[]{Items.DIAMOND_BOOTS,Items.DIAMOND_LEGGINGS,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_HELMET};default->new Item[]{Items.NETHERITE_BOOTS,Items.NETHERITE_LEGGINGS,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_HELMET};};
            require(!p.getInventory().getItem(38).is(armor[2]),"已装备同款护甲");for(int i=0;i<4;i++)p.getInventory().setItem(36+i,new ItemStack(armor[i]));
        }else{
            for(int i=3;i<36&&!stack.isEmpty();i++)if(p.getInventory().getItem(i).isEmpty()){p.getInventory().setItem(i,stack.copy());stack=ItemStack.EMPTY;}
            if(!stack.isEmpty()){p.getInventory().load(before);throw new IllegalArgumentException("背包已满，未扣款");}
        }
        if(coins){t.putInt("credits",t.getInt("credits")-price);t.putInt("shopRevision",t.getInt("shopRevision")+1);store(p,t);}else r.wallet.put(p.getUUID(),r.wallet.getOrDefault(p.getUUID(),0)-price);
        r.orders.put(p.getUUID(),revision+1);r.holds.remove(p.getUUID());p.inventoryMenu.broadcastChanges();
    }
    private CompoundTag progress(ServerPlayer p){
        CompoundTag t=p.getPersistentData().getCompound(STATE);
        if(t.getInt("currencyVersion")<1){int old=Math.max(0,t.getInt("credits"));t.putInt("credits",old/100);t.putInt("exchangeRemainder",old%100);t.putInt("currencyVersion",1);p.getPersistentData().put(STATE,t);}
        return t;
    }
    public void selectWeapon(ServerPlayer p,boolean primary,int slot){
        Room r=room(p.getUUID());require(!locked(p) && (r==null || r.phase==Phase.LOBBY || r.phase==Phase.BUILDING),"仅准备阶段可选择武器");
        loadouts.put(p.getUUID(),loadouts.getOrDefault(p.getUUID(),ChallengeLoadout.defaults()).select(p,primary,slot));
    }
    private void credit(CompoundTag t,int amount){t.putInt("pendingScore",(int)Math.min(1000000000L,Math.max(0,t.getInt("pendingScore"))+(long)Math.max(0,amount)));}
    private int exchange(CompoundTag t){int score=Math.max(0,t.getInt("pendingScore"));long total=(long)score+Math.max(0,t.getInt("exchangeRemainder"));int coins=(int)(total/ChallengeRules.EXCHANGE_RATE);t.putInt("credits",(int)Math.min(1000000000L,(long)Math.max(0,t.getInt("credits"))+coins));t.putInt("exchangeRemainder",(int)(total%ChallengeRules.EXCHANGE_RATE));t.putInt("lastScore",score);t.remove("pendingScore");return coins;}
    private void exchangePending(ServerPlayer p){CompoundTag t=progress(p);if(t.getInt("pendingScore")<=0)return;int score=t.getInt("pendingScore"),coins=exchange(t);t.putString("last","本局 "+score+" 分 → "+coins+" 兑换币");store(p,t);tell(p,"本局 "+score+" 分，结算 "+coins+" 兑换币；不足 500 分的余数保留。");}
    public void buy(ServerPlayer p,String offerId,int revision){
        require(!locked(p),"离场后使用积分商店");CompoundTag t=progress(p);
        require(revision==t.getInt("shopRevision"),"订单已处理或余额已更新，请刷新商店");
        var offer=ChallengeShop.offers(p).stream().filter(o->o.id().equals(offerId)).findFirst().orElseThrow(()->new IllegalArgumentException("商品不存在"));
        require(t.getInt("credits")>=offer.cost(),"兑换币不足");ItemStack stack=offer.stack(p);require(!stack.isEmpty(),"该商品当前不可用");
        ListTag before=p.getInventory().save(new ListTag());p.getInventory().add(stack);
        if(!stack.isEmpty()){p.getInventory().load(before);p.inventoryMenu.broadcastChanges();throw new IllegalArgumentException("背包空间不足，未扣兑换币");}
        t.putInt("credits",t.getInt("credits")-offer.cost());t.putInt("shopRevision",revision+1);store(p,t);p.inventoryMenu.broadcastChanges();
    }
    public void headshot(ServerPlayer p,LivingEntity victim){
        Room r=room(p.getUUID());if(r!=null && r.alive.contains(p.getUUID()) && r.mobs.contains(victim.getUUID()) && victim.isDeadOrDying())
            headshots.put(victim.getUUID(),new Headshot(p.getUUID(),server.getTickCount()));
    }
    private void store(ServerPlayer p,CompoundTag t){p.getPersistentData().put(STATE,t);ChallengeInventory.save(p);}
    public void claim(ServerPlayer p){
        require(!locked(p),"离场后领取奖励");CompoundTag t=progress(p);ListTag rewards=t.getList("rewards",Tag.TAG_COMPOUND);
        require(!rewards.isEmpty(),"没有待领取奖励");
        var before=p.getInventory().save(new ListTag());
        for(Tag tag:rewards){ItemStack stack=ItemStack.parseOptional(p.registryAccess(),(CompoundTag)tag);p.getInventory().add(stack);if(!stack.isEmpty()){p.getInventory().load(before);p.inventoryMenu.broadcastChanges();throw new IllegalArgumentException("背包空间不足，请整理后领取；奖励仍保留");}}
        t.remove("rewards");store(p,t);p.inventoryMenu.broadcastChanges();
    }
    private boolean taskReady(CompoundTag t,int i){return switch(i){case 0->t.getInt("wins")>=1;case 1->t.getInt("kills")>=100;case 2->t.getInt("hardWins")>=1;default->false;};}
    public void claimTask(ServerPlayer p,int i){
        require(!locked(p),"离场后领取任务奖励");CompoundTag t=progress(p);require(i>=0&&i<3 && taskReady(t,i) && !t.getBoolean("task"+i),"任务未完成或已领取");
        t.putInt("credits",Math.min(1000000000,t.getInt("credits")+ChallengeShop.taskCredits(i)));t.putBoolean("task"+i,true);store(p,t);
    }
    private void finish(Room r,boolean won,String reason){
        if(!rooms.remove(r))return;
        r.bossBar.removeAllPlayers();
        ServerLevel level=server.getLevel(ChallengeArena.DIMENSION);
        if(level!=null){r.arena.lamps(level,List.of(),false);for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(r.arena.origin(),0,0,r.arena.origin()+ChallengeArena.SIZE,256,ChallengeArena.SIZE)))if(!(entity instanceof ServerPlayer))entity.discard();r.arena.keepLoaded(level,false);}
        int elapsed=Math.max(0,(server.getTickCount()-r.started)/20);
        for(UUID id:r.members){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null)continue;
            ChallengeInventory.restore(p);
            if(won && r.alive.contains(id)){
                int bonus=ChallengeRules.score(r.difficulty,0,r.wave,elapsed,true),score=r.earned.getOrDefault(id,0)+bonus,m=r.difficulty.reward;
                CompoundTag t=progress(p);t.putInt("wins",t.getInt("wins")+1);if(m>=2)t.putInt("hardWins",t.getInt("hardWins")+1);
                String result="通关 · "+r.difficulty.title+" · "+score+" 分 · "+ChallengeRules.rank(score,r.difficulty)+" 级";
                credit(t,bonus);int coins=exchange(t);t.putInt("best",Math.max(t.getInt("best"),score));t.putString("last",result+" → "+coins+" 兑换币");store(p,t);tell(p,result+"，结算 "+coins+" 兑换币。");
            }else {exchangePending(p);tell(p,reason+"；已得分按比例结算，已恢复原背包和位置。");}
            send(p,reason);
        }
    }
    public void planWave(Room r,int wave){
        List<ChallengeArena.SpawnSite> active=new ArrayList<>();active.add(r.arena.sites().getFirst());
        for(int floor=0;floor<3;floor++){
            if(floor==1&&wave<8||floor==2&&wave<15)continue;
            final int f=floor;var eligible=r.arena.sites().stream().filter(s->s.floor()==f&&!s.id().equals("core")).toList();
            int count=floor==0?Math.min(3,1+(wave-1)/5):floor==1?2:1;
            int start=Math.floorMod(r.id.hashCode()+wave*5,eligible.size());for(int i=0;i<count;i++)active.add(eligible.get((start+i)%eligible.size()));
        }
        r.activeSites=List.copyOf(active);r.preparedWave=wave;r.arena.lamps(server.getLevel(ChallengeArena.DIMENSION),r.activeSites,false);r.flash=false;
        notice(r,"第 "+wave+" 波启用："+String.join("、",r.activeSites.stream().map(ChallengeArena.SpawnSite::name).toList()));
    }
    private void wave(Room r){
        if(r.preparedWave!=r.wave+1)planWave(r,r.wave+1);if(r.wave==0)r.started=server.getTickCount();
        r.wave++;r.phase=Phase.RUNNING;r.issued=0;r.planned=ChallengeRules.count(r.wave,r.alive.size());
        r.batchSize=ChallengeRules.batchSize(r.wave,r.alive.size());r.batchCount=Math.max(1,(r.planned+r.batchSize-1)/r.batchSize);while(r.batchCount>1&&r.planned/r.batchCount<10)r.batchCount--;r.batchNumber=0;r.batchLimit=0;
        r.timer=server.getTickCount()+ChallengeRules.WAVE_SECONDS*20;r.reinforcementIssued=0;r.reinforcements.clear();
        notice(r,"第 "+r.wave+" / "+r.difficulty.waves+" 波："+ChallengeRules.waveName(r.wave,r.difficulty));nextBatch(r);
    }
    private void nextBatch(Room r){
        int now=server.getTickCount();if(r.batchNumber>0)r.tempo=ChallengeRules.adaptTempo(r.tempo,now-r.batchStarted,r.batchDuration,r.batchMobs.isEmpty());
        r.batchNumber++;r.batchQuota=r.planned/r.batchCount+(r.batchNumber<=r.planned%r.batchCount?1:0);r.batchLimit=Math.min(r.planned,r.issued+r.batchQuota);r.batchMobs.clear();
        r.batchStarted=now;r.batchDuration=ChallengeRules.batchSeconds(r.batchQuota,r.tempo)*20;r.batchDeadline=now+r.batchDuration;
    }
    private void spawn(Room r,ServerLevel level){
        spawnEnemy(r,level,ChallengeRules.enemy(r.wave,r.difficulty,r.issued),false,true);
    }
    private void spawnEnemy(Room r,ServerLevel level,ChallengeRules.Enemy kind,boolean reinforcement,boolean quota){
        ServerPlayer target=r.alive.stream().map(id->server.getPlayerList().getPlayer(id)).filter(Objects::nonNull).findFirst().orElse(null);if(target==null)return;
        if(r.activeSites.isEmpty())planWave(r,Math.max(1,r.wave));int ordinal=reinforcement?r.reinforcementIssued:r.issued;
        int floor=kind.boss?0:ChallengeRules.spawnFloor(r.wave,ordinal);
        final int f=floor;var eligible=r.activeSites.stream().filter(s->s.floor()==f).toList();
        var site=kind.boss||floor==0&&Math.floorMod(ordinal,4)!=3?r.activeSites.getFirst():eligible.get(Math.floorMod(ordinal/4,eligible.size()));BlockPos pos=site.position();
        // Spread within the broad core gate instead of piling every enemy into one block.
        if(site.id().equals("core"))pos=pos.offset(ordinal%3,0,(ordinal/3)%3-1);
        Mob z=ChallengeEnemies.create(level,kind,r.difficulty,r.wave,r.arena);
        z.getPersistentData().putString(MOB,r.id);
        z.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        z.setOnGround(true);
        if(kind.boss||kind!=ChallengeRules.Enemy.ZOMBIE){z.setCustomName(Component.literal(kind.title+" · 第 "+r.wave+" 波"));z.setCustomNameVisible(true);}
        if(z instanceof Zombie&&r.wave%4==0){z.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));z.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));}
        ChallengeEnemies.target(z,target);if(level.addFreshEntity(z)){r.mobs.add(z.getUUID());if(quota){r.batchMobs.add(z.getUUID());r.issued++;}if(reinforcement){r.reinforcements.add(z.getUUID());r.reinforcementIssued++;}r.damageRemaining.put(z.getUUID(),reinforcement?0d:(double)z.getMaxHealth());if(kind.boss){r.bosses.put(z.getUUID(),kind);if(r.boss==null)r.boss=z.getUUID();r.bossBar.setProgress(1);for(UUID id:r.alive){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null)r.bossBar.addPlayer(p);}}}
    }
    private void tick(ServerTickEvent.Post e){
        if(e.getServer()!=server)return;ticks++;
        damageSamples.values().forEach(samples->samples.removeIf(sample->server.getTickCount()-sample.tick>2));
        damageSamples.values().removeIf(ArrayDeque::isEmpty);
        for(UUID id:Set.copyOf(evacuate)){evacuate.remove(id);ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null)leave(p,"本局已淘汰");}
        for(var entry:List.copyOf(deaths.entrySet())) {
            LivingDeathEvent death=entry.getValue();if(!death.isCanceled() && death.getEntity().isDeadOrDying()){
                Room r=rooms.stream().filter(it->it.id.equals(death.getEntity().getPersistentData().getString(MOB))).findFirst().orElse(null);
                ServerPlayer owner=TaskOwnership.credit(death.getSource());
                if(r!=null)r.batchMobs.remove(entry.getKey());
                if(r!=null && r.mobs.remove(entry.getKey()) && owner!=null && r.alive.contains(owner.getUUID())){
                    Headshot head=headshots.get(entry.getKey());boolean headshot=head!=null && head.player.equals(owner.getUUID()) && server.getTickCount()-head.tick<=2;
                    int points=r.reinforcements.contains(entry.getKey())?0:((r.bosses.containsKey(entry.getKey())?100:10)+(headshot?10:0))*r.difficulty.reward;
                    r.kills.merge(owner.getUUID(),1,Integer::sum);r.earned.merge(owner.getUUID(),points,Integer::sum);r.wallet.merge(owner.getUUID(),points,(a,b)->Math.min(1000000000,a+b));
                    CompoundTag t=progress(owner);t.putInt("kills",Math.min(1000000,t.getInt("kills")+1));if(headshot)t.putInt("headshots",t.getInt("headshots")+1);credit(t,points);owner.getPersistentData().put(STATE,t);dirty.add(owner.getUUID());
                }
                if(r!=null){r.bosses.remove(entry.getKey());r.reinforcements.remove(entry.getKey());r.damageRemaining.remove(entry.getKey());r.lastPositions.remove(entry.getKey());r.stalled.remove(entry.getKey());}
            }deaths.remove(entry.getKey());headshots.remove(entry.getKey());
        }
        headshots.entrySet().removeIf(entry->server.getTickCount()-entry.getValue().tick>2);
        if(ticks%100==0)for(UUID id:Set.copyOf(dirty)){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p!=null)ChallengeInventory.save(p);dirty.remove(id);}
        ServerLevel level=server.getLevel(ChallengeArena.DIMENSION);if(level==null)return;
        for(Room r:List.copyOf(rooms)){
            try{
            if(r.phase==Phase.BUILDING){r.arena.build(level,512);if(r.arena.ready()){r.arena.labels(level);r.phase=Phase.LOBBY;notice(r,"地图准备完成，房主可开始游戏（支持单人）。");}continue;}
            int now=server.getTickCount();
            if(ticks%2==0){boolean lit=now%20<4||now%20>=8&&now%20<12;if(lit!=r.flash){r.flash=lit;r.arena.lamps(level,r.activeSites,lit);}}
            if(r.phase==Phase.LOBBY){if(now-r.created>24000)finish(r,false,"房间等待超时");continue;}
            for(UUID id:Set.copyOf(r.alive)){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null){r.alive.remove(id);continue;}if(p.containerMenu!=p.inventoryMenu)p.closeContainer();if(!dimension(p.level()) || !r.arena.contains(p.getX(),p.getY(),p.getZ()))leave(p,"离开挑战区域");}
            if(r.alive.isEmpty()){finish(r,false,"挑战失败：无人存活");continue;}
            for(UUID id:Set.copyOf(r.holds.keySet())){var player=server.getPlayerList().getPlayer(id);var hold=r.holds.get(id);try{require(player!=null&&now-hold.heartbeat<=8,"补给中断");checkSupply(player);}catch(IllegalArgumentException stopped){r.holds.remove(id);}}
            if(r.phase==Phase.LOADING){if(r.ready.containsAll(r.alive)){r.phase=Phase.COUNTDOWN;r.timer=now+600;planWave(r,1);}else if(now>=r.timer)finish(r,false,"地图加载超时，请更新客户端后重试");continue;}
            if(r.phase==Phase.COUNTDOWN || r.phase==Phase.REST){if(now>=r.timer)wave(r);continue;}
            if(now>=r.timer&&r.bosses.isEmpty()){finish(r,false,"挑战失败：波次超过五分钟");continue;}
            if(ticks%Math.max(4,Math.min(10,(int)Math.round(6*r.tempo)))==0)for(int pulse=0;pulse<2&&r.issued<r.batchLimit&&r.issued<r.planned&&r.mobs.size()<ChallengeRules.MAX_LIVING;pulse++)spawn(r,level);
            if(ticks%20==0){
                double health=0,max=0;
                for(var boss:List.copyOf(r.bosses.entrySet())){
                    Entity entity=level.getEntity(boss.getKey());
                    if(entity instanceof Mob z&&z.isAlive()){health+=z.getHealth();max+=z.getMaxHealth();}
                    else if(entity==null){r.bosses.remove(boss.getKey());r.mobs.remove(boss.getKey());r.batchMobs.remove(boss.getKey());spawnEnemy(r,level,boss.getValue(),false,false);}
                }
                r.boss=r.bosses.keySet().stream().findFirst().orElse(null);
                if(r.boss==null)r.bossBar.removeAllPlayers();else{
                    r.bossBar.setName(Component.literal("围攻Boss ×"+r.bosses.size()+" · 存活期间持续增援"));
                    r.bossBar.setProgress(max==0?1:(float)Math.max(0,Math.min(1,health/max)));
                    // Boss waves do not time out while a boss is alive. Give survivors time to clear afterward.
                    r.timer=Math.max(r.timer,now+ChallengeRules.WAVE_SECONDS*20);
                }
            }
            if(ticks%40==0&&!r.bosses.isEmpty()&&r.issued>=r.planned)for(int i=0;i<2&&r.mobs.size()<ChallengeRules.MAX_LIVING;i++){
                var kind=ChallengeRules.enemy(r.wave,r.difficulty,100+r.reinforcementIssued);spawnEnemy(r,level,kind,true,false);
            }
            if(ticks%20==0)for(UUID id:Set.copyOf(r.mobs)){
                Entity entity=level.getEntity(id);if(!(entity instanceof Mob z)){r.mobs.remove(id);r.batchMobs.remove(id);r.reinforcements.remove(id);r.damageRemaining.remove(id);r.lastPositions.remove(id);r.stalled.remove(id);continue;}
                if(!z.isAlive())continue;
                ServerPlayer target=r.alive.stream().map(u->server.getPlayerList().getPlayer(u)).filter(Objects::nonNull).min(Comparator.comparingDouble(z::distanceToSqr)).orElse(null);
                if(target!=null){
                    ChallengeEnemies.target(z,target);
                    var previous=r.lastPositions.put(id,z.position());
                    int stalled=previous!=null && previous.distanceToSqr(z.position())<0.25 && z.distanceToSqr(target)>9?r.stalled.getOrDefault(id,0)+20:0;r.stalled.put(id,stalled);
                    if(!r.arena.contains(z.getX(),z.getY(),z.getZ())){z.discard();continue;}
                    if(stalled>=160){BlockPos waypoint=r.arena.recoveryWaypoint(z.getX(),z.getY(),z.getZ(),target.getY());z.goalSelector.getAvailableGoals().forEach(goal->{if(goal.getGoal() instanceof ChallengePursuitGoal pursuit)pursuit.recoverTo(waypoint);});r.stalled.put(id,0);}
                }
            }
            // Never skip unspawned members, including while the global living cap blocks delivery.
            if(r.batchNumber<r.batchCount&&r.issued>=r.batchLimit&&(r.batchMobs.isEmpty()||now>=r.batchDeadline))nextBatch(r);
            // A batch timer only releases reinforcements. The big wave still requires EVERY survivor dead.
            if(r.issued>=r.planned && r.mobs.isEmpty()&&r.bosses.isEmpty()) {if(r.wave>=r.difficulty.waves)finish(r,true,"挑战完成");else{r.phase=Phase.REST;r.timer=now+240;planWave(r,r.wave+1);notice(r,"12 秒后下一波；注意爆闪红灯，提前布置火力点。");}}
            }catch(RuntimeException failure){
                org.slf4j.LoggerFactory.getLogger("muxi-game-core/challenge").error("Challenge room {} failed; restoring players",r.id,failure);
                finish(r,false,"挑战异常，已中止本房间并恢复背包，请联系管理员");
            }
        }
        if(ticks%20==0)for(ServerPlayer p:server.getPlayerList().getPlayers())send(p,"");
    }
    private void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p && locked(p)){e.setCanceled(true);p.setHealth(Math.max(1,p.getMaxHealth()));p.invulnerableTime=100;evacuate.add(p.getUUID());Room r=room(p.getUUID());if(r!=null)r.alive.remove(p.getUUID());}}
    private Room damageRoom(ServerPlayer p,LivingEntity victim){
        if(p==null || p.server!=server)return null;Room r=room(p.getUUID());
        return r!=null && r.phase==Phase.RUNNING && r.alive.contains(p.getUUID()) && r.mobs.contains(victim.getUUID()) && dimension(victim.level())?r:null;
    }
    private void beforeDamage(LivingDamageEvent.Pre e){
        if(damageRoom(TaskOwnership.credit(e.getSource()),e.getEntity())==null)return;
        var samples=damageSamples.computeIfAbsent(e.getEntity(),key->new ArrayDeque<>());
        if(samples.size()<16)samples.push(new DamageSample(e.getSource(),e.getEntity().getHealth(),server.getTickCount()));
    }
    private void afterDamage(LivingDamageEvent.Post e){
        var samples=damageSamples.get(e.getEntity());if(samples==null || samples.isEmpty() || samples.peek().source!=e.getSource())return;
        DamageSample before=samples.pop();ServerPlayer p=TaskOwnership.credit(e.getSource());Room r=damageRoom(p,e.getEntity());
        if(r==null || before.tick!=server.getTickCount())return;
        // Health actually removed, after armor/absorption. Overkill and healing cannot mint extra points.
        double actual=Math.min(Math.min(before.health-e.getEntity().getHealth(),e.getNewDamage()),r.damageRemaining.getOrDefault(e.getEntity().getUUID(),0d));
        if(!Double.isFinite(actual) || actual<=0)return;
        r.damageRemaining.computeIfPresent(e.getEntity().getUUID(),(id,left)->Math.max(0,left-actual));
        CompoundTag t=progress(p);double value=t.getDouble("damageFraction")+actual*r.difficulty.reward;
        int points=(int)Math.floor(value);t.putDouble("damageFraction",value-points);
        t.putDouble("damageDone",t.getDouble("damageDone")+actual);credit(t,points);
        r.earned.merge(p.getUUID(),points,Integer::sum);p.getPersistentData().put(STATE,t);dirty.add(p.getUUID());
        r.wallet.merge(p.getUUID(),points,(a,b)->Math.min(1000000000,a+b));
    }
    private void trackDeath(LivingDeathEvent e){if(e.getEntity().getPersistentData().contains(MOB))deaths.put(e.getEntity().getUUID(),e);}
    private void damage(LivingIncomingDamageEvent e){
        if(e.getEntity() instanceof ServerPlayer p && locked(p)){
            Room r=room(p.getUUID());ServerPlayer owner=TaskOwnership.credit(e.getSource());
            if(r==null || !r.alive.contains(p.getUUID()) || r.phase!=Phase.RUNNING || owner!=null || e.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)&&r.arena.safeDrop(p.getX(),p.getY(),p.getZ()))e.setCanceled(true);
        }
        if(e.getEntity().getPersistentData().contains(MOB)){
            if(e.getSource().getEntity() instanceof Mob attacker&&attacker.getPersistentData().getString(MOB).equals(e.getEntity().getPersistentData().getString(MOB))){e.setCanceled(true);return;}
            ServerPlayer p=TaskOwnership.credit(e.getSource());Room r=p==null?null:room(p.getUUID());
            if(p!=null && (r==null || !r.alive.contains(p.getUUID()) || !r.id.equals(e.getEntity().getPersistentData().getString(MOB))))e.setCanceled(true);
        }
    }
    private void drops(LivingDropsEvent e){if(dimension(e.getEntity().level()))e.setCanceled(true);}
    private void xp(LivingExperienceDropEvent e){if(dimension(e.getEntity().level()))e.setDroppedExperience(0);}
    private void join(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide() || !dimension(e.getLevel()))return;
        if(e.getEntity() instanceof Mob && (!e.getEntity().getPersistentData().contains(MOB) || rooms.stream().noneMatch(r->r.id.equals(e.getEntity().getPersistentData().getString(MOB)))))e.setCanceled(true);
        if(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity)e.setCanceled(true);
    }
    private void interact(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p) || !dimension(p.level()))return;
        e.setCanceled(true);Room r=room(p.getUUID());if(r==null || !r.alive.contains(p.getUUID()) || p.distanceToSqr(e.getPos().getCenter())>36)return;
        BlockPos pos=e.getPos();
        // The station label and personal cooldown are rendered on each client, never in shared world text.
    }
    private void entityInteract(PlayerInteractEvent.EntityInteract e){if(e.getEntity() instanceof ServerPlayer p && locked(p))e.setCanceled(true);}
    private void travel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent e){
        if(e.getDimension().equals(ChallengeArena.DIMENSION) && !(e.getEntity() instanceof ServerPlayer p && locked(p)))e.setCanceled(true);
    }
    private void breakBlock(BlockEvent.BreakEvent e){if(dimension(e.getPlayer().level()))e.setCanceled(true);}
    private void place(BlockEvent.EntityPlaceEvent e){if(e.getEntity()!=null && dimension(e.getEntity().level()))e.setCanceled(true);}
    private void explosion(ExplosionEvent.Detonate e){if(dimension(e.getLevel()))e.getAffectedBlocks().clear();}
    private void toss(ItemTossEvent e){if(e.getPlayer() instanceof ServerPlayer p && locked(p)){e.setCanceled(true);p.getInventory().add(e.getEntity().getItem());}}
    private void command(CommandEvent e){ServerPlayer p=e.getParseResults().getContext().getSource().getPlayer();if(p==null || !locked(p))return;String command=e.getParseResults().getReader().getString().stripLeading();if(command.startsWith("/"))command=command.substring(1);String root=command.split(" ",2)[0];if(!Set.of("muxichallenge","msg","tell","w","r").contains(root)){e.setCanceled(true);tell(p,"挑战中请使用房间界面离场后再执行其他指令");}}
    private void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("muxichallenge").executes(c->{handle(c.getSource().getPlayerOrException(),"list","");return 1;})
            .then(Commands.argument("action",StringArgumentType.word()).executes(c->{handle(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"action"),"");return 1;})
            .then(Commands.argument("value",StringArgumentType.word()).executes(c->{handle(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"action"),StringArgumentType.getString(c,"value"));return 1;}))));
    }
    public String snapshot(ServerPlayer p,String message){
        JsonObject o=new JsonObject();o.addProperty("notice",message);o.addProperty("self",p.getUUID().toString());o.addProperty("available",server.getLevel(ChallengeArena.DIMENSION)!=null);
        JsonArray list=new JsonArray();for(Room r:rooms){JsonObject row=new JsonObject();row.addProperty("id",r.id);row.addProperty("host",r.host.toString());ServerPlayer h=server.getPlayerList().getPlayer(r.host);row.addProperty("name",h==null?"队伍":h.getDisplayName().getString());row.addProperty("difficulty",r.difficulty.name());row.addProperty("phase",r.phase.name());JsonArray sites=new JsonArray();for(var site:r.activeSites)sites.add(site.name());row.add("sites",sites);row.addProperty("incomingWave",r.preparedWave);row.addProperty("wave",r.wave);row.addProperty("total",r.difficulty.waves);row.addProperty("remaining",r.mobs.size()+r.planned-r.issued);row.addProperty("aliveCount",r.mobs.size());row.addProperty("batch",r.batchNumber);row.addProperty("batchCount",r.batchCount);row.addProperty("batchAlive",r.batchMobs.size());row.addProperty("batchUnspawned",Math.max(0,r.batchLimit-r.issued));row.addProperty("batchSeconds",r.batchNumber<r.batchCount?Math.max(0,(r.batchDeadline-server.getTickCount()+19)/20):0);row.addProperty("batchBlocked",r.mobs.size()>=ChallengeRules.MAX_LIVING&&r.issued<r.batchLimit);row.addProperty("seconds",Math.max(0,(r.timer-server.getTickCount())/20));row.addProperty("count",r.members.size());row.addProperty("mine",r.members.contains(p.getUUID()));row.addProperty("invited",r.invites.getOrDefault(p.getUUID(),0L)>server.getTickCount());JsonArray names=new JsonArray();for(UUID id:r.members){ServerPlayer member=server.getPlayerList().getPlayer(id);if(member!=null)names.add(member.getDisplayName().getString());}row.add("members",names);row.addProperty("bossCount",r.bosses.size());row.addProperty("reinforcementIssued",r.reinforcementIssued);list.add(row);}o.add("rooms",list);
        JsonArray online=new JsonArray();for(ServerPlayer q:server.getPlayerList().getPlayers())if(q!=p && online.size()<64){JsonObject row=new JsonObject();row.addProperty("id",q.getUUID().toString());row.addProperty("name",q.getDisplayName().getString());online.add(row);}o.add("players",online);
        CompoundTag t=progress(p);o.addProperty("wins",t.getInt("wins"));o.addProperty("kills",t.getInt("kills"));o.addProperty("best",t.getInt("best"));o.addProperty("last",t.getString("last"));o.addProperty("rewards",t.getList("rewards",Tag.TAG_COMPOUND).size());
        o.addProperty("credits",t.getInt("credits"));o.addProperty("coins",t.getInt("credits"));o.addProperty("exchangeRate",ChallengeRules.EXCHANGE_RATE);o.addProperty("exchangePreview",(t.getInt("pendingScore")+t.getInt("exchangeRemainder"))/ChallengeRules.EXCHANGE_RATE);o.addProperty("headshots",t.getInt("headshots"));o.addProperty("shopRevision",t.getInt("shopRevision"));
        Room own=room(p.getUUID());o.addProperty("earned",own==null?0:own.earned.getOrDefault(p.getUUID(),0));o.addProperty("locked",locked(p));
        o.addProperty("arenaVersion",4);o.addProperty("arenaOrigin",own==null?0:own.arena.origin());o.addProperty("ammoCooldown",own==null?0:Math.max(0,(own.supplies.getOrDefault(p.getUUID()+":ammo",-10000)+ChallengeRules.AMMO_COOLDOWN-server.getTickCount()+19)/20));
        AmmoHold hold=own==null?null:own.holds.get(p.getUUID());o.addProperty("ammoHolding",hold!=null);o.addProperty("ammoProgress",hold==null?0:Math.min(ChallengeRules.AMMO_HOLD,server.getTickCount()-hold.started));o.addProperty("ammoHoldTicks",ChallengeRules.AMMO_HOLD);
        o.addProperty("tactical",own==null?0:own.wallet.getOrDefault(p.getUUID(),0));o.addProperty("battleRevision",own==null?0:own.orders.getOrDefault(p.getUUID(),0));o.addProperty("maxLiving",ChallengeRules.MAX_LIVING);
        JsonArray battle=new JsonArray();if(ModList.get().isLoaded("tacz"))for(var offer:ChallengeBattleShop.offers(p)){JsonObject row=new JsonObject();row.addProperty("id",offer.id());row.addProperty("title",offer.title());row.addProperty("category",offer.category());row.addProperty("gun",offer.gun());row.addProperty("cost",offer.cost());row.addProperty("coins",offer.coins());if(!offer.gun().isEmpty()){var power=ChallengeGunPower.estimate(offer.stack(p));row.addProperty("burstDps",Math.round(power.burstDps()));row.addProperty("sustainedDps",Math.round(power.sustainedDps()));row.addProperty("magazine",power.magazine());}battle.add(row);}o.add("battleShop",battle);
        JsonArray shop=new JsonArray();for(var offer:ChallengeShop.offers(p)){if(offer.stack(p).isEmpty())continue;JsonObject row=new JsonObject();row.addProperty("id",offer.id());row.addProperty("title",offer.title());row.addProperty("cost",offer.cost());row.addProperty("recipePriced",!offer.gun().isEmpty());shop.add(row);}o.add("shop",shop);
        var selected=loadouts.getOrDefault(p.getUUID(),ChallengeLoadout.defaults());o.addProperty("primary",selected.primary());o.addProperty("primary2",selected.primary2());o.addProperty("secondary",selected.secondary());o.addProperty("carryFee",ModList.get().isLoaded("tacz")?selected.fee(p):0);
        JsonArray weapons=new JsonArray();if(!locked(p) && ModList.get().isLoaded("tacz"))for(int i=0;i<36;i++){ItemStack stack=p.getInventory().getItem(i);if(!ChallengeGuns.isGun(stack))continue;JsonObject row=new JsonObject();row.addProperty("slot",i);row.addProperty("name",stack.getHoverName().getString());row.addProperty("pistol",ChallengeGuns.pistol(stack));row.addProperty("fee",ChallengeBattleShop.carryFee(p,stack));weapons.add(row);}o.add("weapons",weapons);
        JsonArray tasks=new JsonArray();for(int i=0;i<3;i++){JsonObject task=new JsonObject();task.addProperty("title",ChallengeRules.TASKS.get(i));task.addProperty("ready",taskReady(t,i));task.addProperty("claimed",t.getBoolean("task"+i));tasks.add(task);}o.add("tasks",tasks);return o.toString();
    }
    private void send(ServerPlayer p,String message){ChallengeNetwork.send(p,snapshot(p,message));if(!message.isEmpty())tell(p,message);}
}
