package net.muxigame.core.feature.tasks;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.tasks.integration.ChampionTaskHooks;
import net.muxigame.core.feature.tasks.integration.TaczTaskHooks;
import net.muxigame.core.mixin.PlayerListSaveInvoker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Server-authoritative stats, date selection and inventory grants. All handlers run on the server thread. */
public final class DailyTasksFeature implements ServerFeature {
    private static final Logger LOG=LoggerFactory.getLogger("muxi-game-core/tasks");
    private static final String PERSISTED="PlayerPersisted", STATE="muxi_daily_tasks";
    private static final Map<MinecraftServer,DailyTasksFeature> ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
    private record Resolved(List<Stat<?>> counters,List<ItemStack> rewards) {}
    private final Map<String,Resolved> resolved=new HashMap<>();
    private final Map<UUID,DailyTaskState> sessions=new HashMap<>();
    private final Map<UUID,Long> requests=new HashMap<>();
    private final Set<UUID> blocked=new HashSet<>();
    private final Set<UUID> dirty=new HashSet<>();
    private record PendingDeath(ServerPlayer player,LivingEntity victim,int tier,boolean gun,
                                LivingDeathEvent event,java.time.LocalDate day,DailyTaskState assignment,Set<String> taskIds) {}
    private final Map<UUID,PendingDeath> pendingDeaths=new LinkedHashMap<>();
    private final Set<LivingEntity> countedDeaths=Collections.newSetFromMap(new WeakHashMap<>());
    private TaczTaskHooks tacz;
    private GatheringTaskHooks gathering;
    private MinecraftServer server;
    private TaskCatalog catalog;
    private int ticks;

    public static DailyTasksFeature active(MinecraftServer server) { return ACTIVE.get(server); }
    @Override public String id() { return "daily-tasks"; }
    @Override public void register(IEventBus bus) {
        bus.addListener(this::onStarted); bus.addListener(this::onStopping); bus.addListener(this::onStopped); bus.addListener(this::onTick);
        bus.addListener(this::onLogin); bus.addListener(this::onLogout); bus.addListener(this::onClone);
        bus.addListener(this::onRespawn); bus.addListener(this::onCommands);
        // Keep the event reference even for canceled deaths. A TaCZ Kill event alone is not proof of death.
        bus.addListener(EventPriority.LOWEST,true,this::onDeath);
        if(ModList.get().isLoaded("tacz")) tacz=new TaczTaskHooks(this,bus);
        gathering=new GatheringTaskHooks(this,bus);
    }
    private void onStarted(ServerStartedEvent event) {
        close(); server=event.getServer(); ACTIVE.put(server,this);
        try { reload(); }
        catch(Exception e) { catalog=null; LOG.error("Daily tasks disabled; check public task configuration",e); }
    }
    private void reload() throws Exception {
        TaskCatalog next=TaskCatalog.load(FMLPaths.CONFIGDIR.get().resolve(Path.of(TaskCatalog.FILE).getFileName()));
        next=next.available(d->{
            boolean available=d.requiresMods().stream().allMatch(id->ModList.get().isLoaded(id));
            if(d.kind()==TaskCatalog.Kind.CHAMPION_KILLED) available &= ModList.get().isLoaded("champions");
            if(d.kind()==TaskCatalog.Kind.GUN_HIT || d.kind()==TaskCatalog.Kind.GUN_HEADSHOT || d.kind()==TaskCatalog.Kind.GUN_KILL)
                available &= ModList.get().isLoaded("tacz");
            if(!available) LOG.info("Task {} omitted because an optional mod is absent",d.id());
            return available;
        });
        Map<String,Resolved> validated=new HashMap<>();
        if(next.enabled()) for(var d:next.pool()) validated.put(d.json().toString(),resolve(d));
        // Invalid reload never discards the previous good catalog or today's reward snapshots.
        catalog=next; resolved.putAll(validated); blocked.clear();
        LOG.info("Daily tasks: enabled={}, normal={}, hard={}, pool={}, reset={}:00 {}",next.enabled(),next.dailyCount(),next.hardCount(),next.pool().size(),next.resetHour(),next.zone());
    }
    private boolean enabled() { return catalog!=null && catalog.enabled(); }
    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) update(p,true,false,"");
    }
    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) {
            if(enabled() && sessions.containsKey(p.getUUID())) update(p,false,false,"");
            sessions.remove(p.getUUID()); requests.remove(p.getUUID()); blocked.remove(p.getUUID()); dirty.remove(p.getUUID());
        }
    }
    private void onClone(PlayerEvent.Clone event) {
        if(event.getEntity().level().isClientSide()) return;
        // A combat event can arrive before the next one-second HUD update. Preserve that progress too.
        if(event.getOriginal() instanceof ServerPlayer original && sessions.containsKey(original.getUUID()))
            persist(original,sessions.get(original.getUUID()));
        String state=event.getOriginal().getPersistentData().getCompound(PERSISTED).getString(STATE);
        if(!state.isEmpty()) {
            CompoundTag root=event.getEntity().getPersistentData(); CompoundTag tag=root.getCompound(PERSISTED);
            tag.putString(STATE,state); root.put(PERSISTED,tag);
        }
    }
    private void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if(event.getEntity() instanceof ServerPlayer p) update(p,true,false,"");
    }
    private void onTick(ServerTickEvent.Post event) {
        if(event.getServer()!=server || !enabled()) return;
        drainDeaths(); gathering.flush();
        if(++ticks%20!=0) return;
        for(ServerPlayer p:server.getPlayerList().getPlayers()) if(!(p instanceof FakePlayer))
            update(p,ticks%600==0,false,"");
    }
    private void onStopping(ServerStoppingEvent event) {
        if(event.getServer()!=server) return;
        for(ServerPlayer p:server.getPlayerList().getPlayers()) if(sessions.containsKey(p.getUUID())) persist(p,sessions.get(p.getUUID()));
        ACTIVE.remove(server);
    }
    /** Shooting tasks never encourage farming players, pets or passive creatures. */
    public boolean acceptsCombat(ServerPlayer player,LivingEntity target) {
        return enabled() && player.server==server && !(player instanceof FakePlayer) && !player.isSpectator()
            && !target.level().isClientSide() && !(target instanceof Player)
            && !(target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame())
            && !(target instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerUUID()!=null)
            && (target instanceof Enemy || target.getType().getCategory()==MobCategory.MONSTER);
    }
    public void combatProgress(ServerPlayer player,TaskCatalog.Kind kind,LivingEntity target,int tier) {
        combatProgress(player,kind,target,tier,null);
    }
    private void combatProgress(ServerPlayer player,TaskCatalog.Kind kind,LivingEntity target,int tier,Set<String> eligibleIds) {
        if(!acceptsCombat(player,target) || blocked.contains(player.getUUID())) return;
        if(!server.isSameThread()) throw new IllegalStateException("Task combat progress must run on the server thread");
        try {
            DailyTaskState state=state(player,Instant.now());
            if(state.record(kind,BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(),tier,eligibleIds)) {
                // This only updates in-memory player NBT. No per-shot disk writes or world scans.
                persist(player,state); dirty.add(player.getUUID());
            }
        } catch(RuntimeException e) {
            blocked.add(player.getUUID()); LOG.error("Task event rejected for {}",player.getUUID(),e);
        }
    }
    private int championTier(LivingEntity target) { return ModList.get().isLoaded("champions")?ChampionTaskHooks.tier(target):0; }
    private void onDeath(LivingDeathEvent event) {
        ServerPlayer player=TaskOwnership.credit(event.getSource());
        if(player!=null && acceptsCombat(player,event.getEntity())) {
            int tier=championTier(event.getEntity());
            queueDeath(player,event.getEntity(),tier,false,event);
        }
    }
    public void gunKill(ServerPlayer player,LivingEntity target) {
        if(acceptsCombat(player,target)) queueDeath(player,target,championTier(target),true,null);
    }
    private void queueDeath(ServerPlayer player,LivingEntity target,int tier,boolean gun,LivingDeathEvent event) {
        if(countedDeaths.contains(target) || blocked.contains(player.getUUID())) return;
        PendingDeath old=pendingDeaths.get(target.getUUID());
        if(old!=null) {
            if(!old.player().getUUID().equals(player.getUUID())) return;
            tier=Math.max(tier,old.tier()); gun|=old.gun(); if(event==null) event=old.event();
        }
        try {
            DailyTaskState assigned=old!=null?old.assignment():state(player,Instant.now());
            Set<String> ids=old!=null?old.taskIds():assigned.entries().stream().map(e->e.definition.id()).collect(java.util.stream.Collectors.toSet());
            pendingDeaths.put(target.getUUID(),new PendingDeath(player,target,tier,gun,event,assigned.day(),assigned,ids));
        } catch(RuntimeException e) {
            blocked.add(player.getUUID()); LOG.error("Task death event rejected for {}",player.getUUID(),e);
        }
    }
    private void drainDeaths() {
        for(PendingDeath d:pendingDeaths.values()) {
            if((d.event()!=null && d.event().isCanceled()) || !d.victim().isDeadOrDying()
                || !d.day().equals(catalog.day(Instant.now())) || sessions.get(d.player().getUUID())!=d.assignment()
                || !countedDeaths.add(d.victim())) continue;
            if(d.gun()) combatProgress(d.player(),TaskCatalog.Kind.GUN_KILL,d.victim(),0,d.taskIds());
            combatProgress(d.player(),TaskCatalog.Kind.DEFEATED,d.victim(),0,d.taskIds());
            if(d.tier()>0) combatProgress(d.player(),TaskCatalog.Kind.CHAMPION_KILLED,d.victim(),d.tier(),d.taskIds());
        }
        pendingDeaths.clear();
    }
    private void onStopped(ServerStoppedEvent event) { if(event.getServer()==server) close(); }
    private DailyTaskState state(ServerPlayer player,Instant now) {
        DailyTaskState state=sessions.get(player.getUUID());
        if(state==null) {
            String json=player.getPersistentData().getCompound(PERSISTED).getString(STATE);
            if(!json.isEmpty()) state=DailyTaskState.parse(json); // Corruption is not an excuse to roll free new rewards.
        }
        if(state==null || state.expired(catalog,now))
            state=DailyTaskState.create(catalog,player.getUUID(),server.overworld().getSeed(),now,d->counter(player,d),state);
        sessions.put(player.getUUID(),state); return state;
    }
    private void update(ServerPlayer player,boolean force,boolean open,String notice) {
        if(player instanceof FakePlayer || blocked.contains(player.getUUID())) return;
        if(!enabled()) {
            if(force) TaskNetwork.send(player,new TaskNetwork.Snapshot("",0,Instant.now().getEpochSecond(),List.of(),open,"每日任务未启用"));
            return;
        }
        try {
            Instant now=Instant.now(); DailyTaskState old=sessions.get(player.getUUID()), state=state(player,now);
            boolean changed=state.observe(d->counter(player,d)) | dirty.remove(player.getUUID());
            if(changed || old!=state) persist(player,state);
            if(force || changed || old!=state) send(player,state,open,notice);
        } catch(RuntimeException e) {
            blocked.add(player.getUUID()); LOG.error("Task data unavailable for {}: {}",player.getUUID(),e.getMessage());
            TaskNetwork.send(player,new TaskNetwork.Snapshot("",0,Instant.now().getEpochSecond(),List.of(),open,"任务数据暂不可用，请联系管理员"));
        }
    }
    private void persist(ServerPlayer player,DailyTaskState state) {
        CompoundTag root=player.getPersistentData(), tag=root.getCompound(PERSISTED);
        tag.putString(STATE,state.json()); root.put(PERSISTED,tag);
    }
    private Resolved resolved(TaskCatalog.Definition d) { return resolved.computeIfAbsent(d.json().toString(),ignored->resolve(d)); }
    private Resolved resolve(TaskCatalog.Definition d) {
        List<Stat<?>> stats=new ArrayList<>();
        for(String name:d.targets()) {
            ResourceLocation id=ResourceLocation.parse(name);
            switch(d.kind()) {
                case MINED -> { require(BuiltInRegistries.BLOCK.containsKey(id),name); stats.add(Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.get(id))); }
                case CRAFTED -> { require(BuiltInRegistries.ITEM.containsKey(id),name); stats.add(Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.get(id))); }
                case KILLED -> { require(BuiltInRegistries.ENTITY_TYPE.containsKey(id),name); stats.add(Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.get(id))); }
                case CUSTOM -> {
                    require(BuiltInRegistries.CUSTOM_STAT.containsKey(id),name);
                    // Registry values are identity-keyed: an equal, freshly parsed ResourceLocation is NOT the registered value.
                    stats.add(Stats.CUSTOM.get(BuiltInRegistries.CUSTOM_STAT.get(id)));
                }
                case CHAMPION_KILLED, GUN_HIT, GUN_HEADSHOT, GUN_KILL, DEFEATED -> require(BuiltInRegistries.ENTITY_TYPE.containsKey(id),name);
                case HARVESTED, FLOWERS -> require(BuiltInRegistries.BLOCK.containsKey(id),name);
            }
        }
        var ops=RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()); List<ItemStack> rewards=new ArrayList<>();
        for(var json:d.rewards()) {
            if(json.has("variants")) {
                ItemStack first=null;
                for(var element:json.getAsJsonArray("variants")) {
                    com.google.gson.JsonObject variant=element.getAsJsonObject().deepCopy(); variant.remove("weight");
                    ItemStack checked=parseReward(variant,ops,d.id());
                    if(first==null) first=checked;
                }
                rewards.add(first); continue;
            }
            rewards.add(parseReward(json,ops,d.id()));
        }
        return new Resolved(List.copyOf(stats),List.copyOf(rewards));
    }
    private ItemStack parseReward(com.google.gson.JsonObject json,RegistryOps<com.google.gson.JsonElement> ops,String taskId) {
            ItemStack stack;
            if(json.has("ammoId")) {
                require(ModList.get().isLoaded("tacz") && "tacz:ammo".equals(json.get("id").getAsString()),"ammo reward");
                require(!json.has("components"),"ammoId rewards use the native ammunition builder");
                stack=TaczTaskHooks.ammo(json.get("ammoId").getAsString(),TaskCatalog.number(json,"count",1,1,64));
            } else stack=ItemStack.CODEC.parse(ops,json).getOrThrow();
            int maxCount=TaskCatalog.number(json,"countMax",stack.getCount(),stack.getCount(),64);
            require(!stack.isEmpty() && maxCount<=Math.min(64,stack.getMaxStackSize()),"reward for "+taskId);
            return stack;
    }
    private static void require(boolean valid,String what) { if(!valid) throw new IllegalArgumentException("Unknown/invalid "+what); }
    /** Capture before delayed world/drop validation so rerolls cannot inherit an earlier action. */
    public Runnable gatheringCredit(ServerPlayer player,TaskCatalog.Kind kind,String target,int amount) {
        return gatheringCredit(player,kind,target,()->amount);
    }
    public Runnable gatheringCredit(ServerPlayer player,TaskCatalog.Kind kind,String target,java.util.function.IntSupplier amount) {
        if(!enabled() || player.server!=server || player instanceof FakePlayer || player.isSpectator() || blocked.contains(player.getUUID())) return ()->{};
        DailyTaskState assigned;
        try { assigned=state(player,Instant.now()); }
        catch(RuntimeException error) {
            blocked.add(player.getUUID()); LOG.error("Gathering task data unavailable for {}",player.getUUID(),error);
            return ()->{};
        }
        Set<String> ids=new HashSet<>(); assigned.entries().forEach(e->ids.add(e.definition.id()));
        return ()->{
            if(!enabled() || sessions.get(player.getUUID())!=assigned || !assigned.day().equals(catalog.day(Instant.now()))) return;
            if(assigned.record(kind,target,0,ids,amount.getAsInt())) { persist(player,assigned); dirty.add(player.getUUID()); }
        };
    }
    private long counter(ServerPlayer player,TaskCatalog.Definition d) {
        if(d.kind().eventDriven()) return 0;
        long value=0; for(Stat<?> stat:resolved(d).counters()) value+=Math.max(0,player.getStats().getValue(stat)); return value;
    }
    private void send(ServerPlayer player,DailyTaskState state,boolean open,String notice) {
        if(!notice.isEmpty() && player.connection!=null) player.displayClientMessage(Component.literal(notice),true);
        if(!TaskNetwork.supported(player)) return;
        List<TaskNetwork.Row> rows=new ArrayList<>();
        for(var e:state.entries()) {
            var d=e.definition;
            rows.add(new TaskNetwork.Row(d.id(),d.title(),d.description(),e.progress(),d.goal(),d.unit(),e.claimed(),
                resolved(d).rewards().stream().map(ItemStack::copy).toList(),d.experienceLevels(),d.hard()));
        }
        TaskNetwork.send(player,new TaskNetwork.Snapshot(state.day().toString(),state.resetAt(),Instant.now().getEpochSecond(),rows,open,notice,state.rerollsRemaining()));
    }
    private boolean rateLimit(ServerPlayer player) {
        if(player instanceof FakePlayer) return false;
        long now=System.nanoTime(), last=requests.getOrDefault(player.getUUID(),0L);
        if(last!=0 && now-last<200_000_000L) return false;
        requests.put(player.getUUID(),now); return true;
    }
    public void request(ServerPlayer player,boolean open) {
        if(rateLimit(player)) update(player,true,open,"");
    }
    public void reroll(ServerPlayer player,String day,String id) {
        if(!rateLimit(player) || !enabled() || blocked.contains(player.getUUID())) return;
        update(player,false,false,""); DailyTaskState state=sessions.get(player.getUUID());
        if(state==null || blocked.contains(player.getUUID())) return;
        if(!player.isAlive() || player.isSpectator()) { send(player,state,false,"请在正常游戏状态下更换任务"); return; }
        var result=state.reroll(catalog,player.getUUID(),server.overworld().getSeed(),day,id,d->counter(player,d));
        String notice=switch(result) {
            case SUCCESS -> "任务已更换；今日免费更换次数已用完，新任务从零开始";
            case STALE -> "任务已经刷新，请重新查看";
            case UNAVAILABLE -> "只能更换尚未完成的任务";
            case USED -> "今日免费更换次数已用完";
            case NO_CANDIDATE -> "没有可替换的同难度任务，本次不消耗次数";
        };
        if(result==DailyTaskState.RerollResult.SUCCESS) {
            persist(player,state);
            ((PlayerListSaveInvoker)server.getPlayerList()).muxi$savePlayer(player);
            LOG.info("Daily task rerolled: player={}, day={}, oldTask={}",player.getUUID(),day,id);
        }
        send(player,state,false,notice);
    }
    public void claim(ServerPlayer player,String day,String id) {
        if(!rateLimit(player) || !enabled() || blocked.contains(player.getUUID())) return;
        update(player,false,false,""); DailyTaskState state=sessions.get(player.getUUID());
        if(state==null || blocked.contains(player.getUUID())) return;
        if(!state.claimable(day,id)) { send(player,state,false,"任务未完成、已领取或已刷新"); return; }
        if(!player.isAlive() || player.isSpectator()) { send(player,state,false,"请在正常游戏状态下领取"); return; }
        var entry=state.find(id); var rewards=resolved(entry.definition).rewards();
        List<ItemStack> kinds=new ArrayList<>(); List<RewardPacking.Slot<Integer>> slots=new ArrayList<>(), additions=new ArrayList<>();
        for(ItemStack stack:player.getInventory().items) slots.add(new RewardPacking.Slot<>(kind(kinds,stack),stack.getCount(),stack.isEmpty()?64:Math.min(64,stack.getMaxStackSize())));
        for(ItemStack stack:rewards) additions.add(new RewardPacking.Slot<>(kind(kinds,stack),stack.getCount(),Math.min(64,stack.getMaxStackSize())));
        var plan=RewardPacking.plan(slots,additions);
        if(plan.isEmpty()) { send(player,state,false,"背包空间不足；腾出位置后再领取"); return; }
        // No work is deferred between validation and grant; the server thread owns this transaction.
        if(!state.markClaimed(day,id)) return;
        List<RewardPacking.Slot<Integer>> packed=plan.get();
        for(int i=0;i<packed.size();i++) {
            var slot=packed.get(i);
            if(!slot.equals(slots.get(i))) player.getInventory().setItem(i,slot.count()==0?ItemStack.EMPTY:kinds.get(slot.kind()).copyWithCount(slot.count()));
        }
        if(entry.definition.experienceLevels()>0) player.giveExperienceLevels(entry.definition.experienceLevels());
        persist(player,state); player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        // Claim bit and rewards live in the SAME player NBT save, not two independently saved databases.
        ((PlayerListSaveInvoker)server.getPlayerList()).muxi$savePlayer(player);
        LOG.info("Daily reward granted: player={}, day={}, task={}",player.getUUID(),day,id);
        send(player,state,false,"奖励已放入背包");
    }
    private static int kind(List<ItemStack> kinds,ItemStack stack) {
        if(stack.isEmpty()) return -1;
        for(int i=0;i<kinds.size();i++) if(ItemStack.isSameItemSameComponents(kinds.get(i),stack)) return i;
        kinds.add(stack.copyWithCount(1)); return kinds.size()-1;
    }
    private void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("muxi").then(Commands.literal("tasks")
            .executes(ctx->{
                ServerPlayer player=ctx.getSource().getPlayerOrException(); request(player,true);
                if(!TaskNetwork.supported(player)) {
                    DailyTaskState state=sessions.get(player.getUUID());
                    if(state==null) ctx.getSource().sendFailure(Component.literal("每日任务暂不可用"));
                    else for(var e:state.entries()) {
                        MutableComponent line=Component.literal((e.definition.hard()?"[困难] ":"")+e.definition.title()+"  "+e.progress()+"/"+e.definition.goal()
                            +"  +"+e.definition.experienceLevels()+"级"+(e.claimed()?"  已领取":""));
                        if(e.ready()) line.append(Component.literal("  [领取]").withStyle(s->s.withColor(0x91C8A2)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/muxi tasks claim "+e.definition.id()))));
                        if(e.replaceable() && state.rerollsRemaining()>0) line.append(Component.literal("  [换一项]").withStyle(s->s
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,"/muxi tasks reroll "+e.definition.id()))));
                        player.sendSystemMessage(line);
                    }
                }
                return 1;
            })
            .then(Commands.literal("claim").then(Commands.argument("id",StringArgumentType.word()).executes(ctx->{
                ServerPlayer player=ctx.getSource().getPlayerOrException();
                if(enabled()) claim(player,catalog.day(Instant.now()).toString(),StringArgumentType.getString(ctx,"id")); return 1;
            })))
            .then(Commands.literal("reroll").then(Commands.argument("id",StringArgumentType.word()).executes(ctx->{
                ServerPlayer player=ctx.getSource().getPlayerOrException();
                if(enabled()) reroll(player,catalog.day(Instant.now()).toString(),StringArgumentType.getString(ctx,"id")); return 1;
            })))
            .then(Commands.literal("reload").requires(s->s.hasPermission(2)).executes(ctx->{
                try {
                    reload();
                    for(ServerPlayer p:server.getPlayerList().getPlayers()) update(p,true,false,"");
                    ctx.getSource().sendSuccess(()->Component.literal("任务配置已重载；已分配任务保留原目标与奖励，下一次刷新使用新任务池"),true); return 1;
                } catch(Exception e) { ctx.getSource().sendFailure(Component.literal("任务配置无效："+e.getMessage())); return 0; }
            }))));
    }
    @Override public void close() {
        if(server!=null) ACTIVE.remove(server); server=null; catalog=null; ticks=0;
        sessions.clear(); requests.clear(); resolved.clear(); blocked.clear(); dirty.clear(); pendingDeaths.clear(); countedDeaths.clear();
        if(tacz!=null) tacz.clear();
        if(gathering!=null) gathering.clear();
    }
}
