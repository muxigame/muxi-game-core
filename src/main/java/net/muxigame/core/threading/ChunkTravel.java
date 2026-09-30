package net.muxigame.core.threading;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;

/** Coordinator-owned admission and speculative tickets; never waits for a chunk future. */
public final class ChunkTravel {
    public static final boolean ENABLED = DimensionThreads.ENABLED && !Boolean.getBoolean("muxi.chunkTravel.disabled");
    private static final TicketType<ChunkPos> TICKET = TicketType.create("muxi_travel", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<MinecraftServer, ChunkTravel> SERVERS = new IdentityHashMap<>();
    private static final int MAX_TICKETS = 48, MAX_WORLD_TICKETS = 24, MAX_REQUESTS = 256;
    private final Map<Key, Integer> tickets = new LinkedHashMap<>();
    private final Map<Key, Integer> requests = new LinkedHashMap<>();
    private final Map<Key, Integer> urgent = new LinkedHashMap<>();
    private final Set<Key> wanted = new HashSet<>();
    private final Map<UUID, Motion> motion = new HashMap<>();
    private final Map<ServerLevel, Pregeneration> jobs = new LinkedHashMap<>();
    private int tick, submittedThisTick, maxTickets, maxRequests, cursor;
    private long admitted, held, submitted, released, budgetPauses;
    private double tickMillis;
    private long tickStart;
    private record Key(ServerLevel level, int x, int z) { ChunkPos pos() { return new ChunkPos(x,z); } }
    private record Motion(ServerLevel level, double x, double z, int tick) {}
    private static final class Pregeneration {
        final int x,z,radius,total;
        int next,completed;
        Key pending;
        Pregeneration(int x,int z,int radius) { this.x=x;this.z=z;this.radius=radius;total=(radius*2+1)*(radius*2+1); }
    }
    public static void register(IEventBus bus) {
        if (!ENABLED) return;
        bus.addListener(ChunkTravel::pre);
        bus.addListener(ChunkTravel::post);
        bus.addListener(ChunkTravel::commands);
        bus.addListener(ChunkTravel::stop);
    }
    private static ChunkTravel runtime(MinecraftServer server) {
        if (!server.isSameThread() || DimensionThreads.inParallelPhase(server))
            throw new IllegalStateException("Travel admission must run on the coordinator outside the world barrier");
        return SERVERS.computeIfAbsent(server, ignored -> new ChunkTravel());
    }
    private static void pre(ServerTickEvent.Pre event) {
        var run=runtime(event.getServer());run.tickStart=System.nanoTime();run.tick++;run.submittedThisTick=0;
        run.update(event.getServer());
    }
    private static void post(ServerTickEvent.Post event) {
        var run=runtime(event.getServer());
        run.tickMillis=run.tickMillis*.8+(System.nanoTime()-run.tickStart)/1e6*.2;
    }
    private static void stop(ServerStoppingEvent event) {
        var run=SERVERS.remove(event.getServer());
        if(run!=null) { for(var key:run.tickets.keySet())run.remove(key);run.tickets.clear(); }
    }
    private void remove(Key key) {key.level.getChunkSource().removeRegionTicket(TICKET,key.pos(),0,key.pos());released++;}
    private boolean ready(Key key) {return key.level.getChunkSource().getChunkNow(key.x,key.z)!=null;}
    private void request(Key key,int priority) {
        if(!key.level.getWorldBorder().isWithinBounds(key.pos()))return;
        wanted.add(key);
        if(tickets.containsKey(key))tickets.put(key,tick+100);
        if(ready(key))return;
        if(requests.size()<MAX_REQUESTS || requests.containsKey(key))requests.merge(key,priority,Math::min);
        else if(priority==0) {
            var worst=requests.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            if(worst.getValue()>0){requests.remove(worst.getKey());requests.put(key,priority);}
        }
        maxRequests=Math.max(maxRequests,requests.size());
    }
    private void submit() {
        // Bound admission, not native continuation queues: dropping/rejecting a native
        // continuation can strand its future. Urgent movement retains one slot even under load.
        boolean busy=tickMillis>40 || DimensionThreads.loadBacklog() > 256;
        int limit=busy?1:3;
        var candidates=new ArrayList<>(requests.entrySet());
        candidates.sort(Map.Entry.comparingByValue());
        for(var entry:candidates) {
            var key=entry.getKey();
            if(ready(key)){requests.remove(key);continue;}
            if(tickets.containsKey(key))continue;
            if(submittedThisTick>=limit)break;
            if(busy && entry.getValue()>0){budgetPauses++;continue;}
            long worldCount=tickets.keySet().stream().filter(k->k.level==key.level).count();
            if(entry.getValue()==0 && (tickets.size()>=MAX_TICKETS || worldCount>=MAX_WORLD_TICKETS)) {
                // A completed speculative ticket must never block a missing collision
                // chunk from admission. Native player tickets continue to protect it.
                boolean sameWorld=worldCount>=MAX_WORLD_TICKETS;
                var victim=tickets.keySet().stream().filter(k->(!sameWorld || k.level==key.level)
                    && !urgent.containsKey(k) && ready(k)).findFirst().orElse(null);
                if(victim!=null){tickets.remove(victim);remove(victim);if(victim.level==key.level)worldCount--;}
            }
            if(tickets.size()>=MAX_TICKETS || worldCount>=MAX_WORLD_TICKETS)continue;
            key.level.getChunkSource().addRegionTicket(TICKET,key.pos(),0,key.pos());
            tickets.put(key,tick+100);submitted++;submittedThisTick++;
            maxTickets=Math.max(maxTickets,tickets.size());
        }
    }
    private void update(MinecraftServer server) {
        requests.clear();
        wanted.clear();
        urgent.entrySet().removeIf(e->e.getValue()<tick || ready(e.getKey()));
        urgent.keySet().forEach(k->request(k,0));
        var live=new HashSet<UUID>();
        var players=new ArrayList<>(server.getPlayerList().getPlayers());
        if(!players.isEmpty())Collections.rotate(players,(cursor++)%players.size());
        for(var player:players) {
            live.add(player.getUUID());
            var old=motion.put(player.getUUID(),new Motion(player.serverLevel(),player.getX(),player.getZ(),tick));
            if(old==null || old.level!=player.serverLevel())continue;
            double dx=player.getX()-old.x,dz=player.getZ()-old.z,speed=Math.hypot(dx,dz);
            if(speed<.03 || speed>16)continue; // stationary and teleports do not speculate
            // Near first; reset direction immediately on a turn rather than predicting
            // past the corner. At most three chunks ahead, never filter vanilla tickets.
            for(int distance=0;distance<=3;distance++) {
                int cx=Mth.floor((player.getX()+dx/speed*distance*16)/16);
                int cz=Mth.floor((player.getZ()+dz/speed*distance*16)/16);
                for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)
                    request(new Key(player.serverLevel(),x,z),distance+1);
            }
        }
        motion.keySet().retainAll(live);
        for(var entry:jobs.entrySet()) {
            var job=entry.getValue();
            if(job.pending!=null && ready(job.pending)){
                job.completed++;
                if(tickets.remove(job.pending)!=null)remove(job.pending);
                job.pending=null;
            }
            if(job.pending==null && job.next<job.total) {
                int width=job.radius*2+1,index=job.next++;
                job.pending=new Key(entry.getKey(),job.x-job.radius+index%width,job.z-job.radius+index/width);
            }
            if(job.pending!=null)request(job.pending,100);
        }
        // Completed tickets are held briefly to bridge movement; stale predictions expire.
        var iterator=tickets.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getValue()<tick){remove(entry.getKey());iterator.remove();}
            else if(ready(entry.getKey()) && !wanted.contains(entry.getKey()))
                entry.setValue(Math.min(entry.getValue(),tick+20));
        }
        submit();
    }
    /** Check the entire swept collision footprint, including a one-chunk mod lookup margin. */
    public static boolean allow(ServerPlayer player,Entity entity,double x,double y,double z) {
        if(!ENABLED)return true;
        var run=runtime(player.server);
        double dx=x-entity.getX(),dy=y-entity.getY(),dz=z-entity.getZ();
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz))return true; // vanilla validation
        if(Math.abs(dx)>64 || Math.abs(dz)>64){run.held++;return false;}
        if(dx==0 && dy==0 && dz==0)return true;
        AABB box=entity.getBoundingBox().expandTowards(dx,dy,dz).inflate(16,0,16);
        int x0=Mth.floor(box.minX/16),x1=Mth.floor(box.maxX/16),z0=Mth.floor(box.minZ/16),z1=Mth.floor(box.maxZ/16);
        if((long)(x1-x0+1)*(z1-z0+1)>64){run.held++;return false;}
        boolean ready=true;
        for(int cx=x0;cx<=x1;cx++)for(int cz=z0;cz<=z1;cz++) {
            var key=new Key(player.serverLevel(),cx,cz);
            if(!run.ready(key)){
                ready=false;
                if(run.urgent.size()<MAX_REQUESTS || run.urgent.containsKey(key))run.urgent.put(key,run.tick+40);
                run.request(key,0);
            }
        }
        if(ready)run.admitted++;else {run.held++;run.submit();}
        return ready;
    }
    public static Map<String,Object> metrics(MinecraftServer server) {
        var run=SERVERS.get(server);
        if(run==null)return Map.of("enabled",false);
        var jobs=new TreeMap<String,Object>();
        run.jobs.forEach((level,job)->jobs.put(level.dimension().location().toString(),Map.of("completed",job.completed,"total",job.total)));
        return Map.of("enabled",true,"admittedMoves",run.admitted,"heldMoves",run.held,"submitted",run.submitted,
            "released",run.released,"tickets",run.tickets.size(),"maxTickets",run.maxTickets,"maxRequests",run.maxRequests,
            "budgetPauses",run.budgetPauses,"pregeneration",jobs);
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("muxichunks").requires(s->s.hasPermission(2))
            .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal(metrics(c.getSource().getServer()).toString()),false);return 1;}))
            .then(Commands.literal("cancel").executes(c->{
                var run=runtime(c.getSource().getServer());var job=run.jobs.remove(c.getSource().getLevel());
                if(job!=null&&job.pending!=null)run.requests.remove(job.pending);
                c.getSource().sendSuccess(()->Component.literal("已停止本维度预生成；现有短期票据会自动释放"),false);return 1;}))
            .then(Commands.literal("pregen").then(Commands.argument("radiusChunks",IntegerArgumentType.integer(1,128)).executes(c->{
                var source=c.getSource();var run=runtime(source.getServer());var pos=source.getPosition();
                if(run.jobs.containsKey(source.getLevel()) && run.jobs.get(source.getLevel()).completed<run.jobs.get(source.getLevel()).total) {
                    source.sendFailure(Component.literal("本维度已有预生成任务，请先取消或等待完成"));return 0;
                }
                run.jobs.put(source.getLevel(),new Pregeneration(Mth.floor(pos.x/16),Mth.floor(pos.z/16),IntegerArgumentType.getInteger(c,"radiusChunks")));
                source.sendSuccess(()->Component.literal("已开始限速预生成，以当前位置为中心；/muxichunks status 查看进度"),false);return 1;
            }))));
    }
}
