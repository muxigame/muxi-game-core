package net.muxigame.core.feature.tasks;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.core.mixin.PlayerListSaveInvoker;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Pending events share the claim/reward NBT save; HTTP never runs on the game thread. */
final class TaskPointsBridge implements AutoCloseable {
    static final String KEY="muxi_task_points_pending";
    private final MinecraftServer server;
    private final URI endpoint;
    private final String key;
    private final ExecutorService worker;
    private final Set<UUID> busy=new HashSet<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private boolean closed;

    TaskPointsBridge(MinecraftServer server) {
        this.server=server;
        key=System.getenv().getOrDefault("MUXI_TASK_POINTS_KEY","");
        URI configured=null;
        if("1".equals(System.getenv("MUXI_TASK_POINTS_ENABLED")) && key.length()>=32) {
            try {
                URI u=URI.create(System.getenv().getOrDefault("MUXI_TASK_POINTS_URL",""));
                boolean local=u.getHost()!=null && Set.of("localhost","127.0.0.1","::1","[::1]").contains(u.getHost());
                if(u.getHost()!=null && u.getUserInfo()==null && u.getQuery()==null && u.getFragment()==null
                   && ("https".equals(u.getScheme()) || local && "http".equals(u.getScheme()))
                   && "/api/internal/game/task-claims".equals(u.getPath())) configured=u;
            } catch(IllegalArgumentException ignored) {}
        }
        endpoint=configured;
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"muxi-task-points");t.setDaemon(true);return t;});
    }
    boolean enabled() { return endpoint!=null && !closed; }
    private static JsonArray pending(ServerPlayer p) {
        String raw=p.getPersistentData().getCompound("PlayerPersisted").getString(KEY);
        return raw.isEmpty()?new JsonArray():JsonParser.parseString(raw).getAsJsonArray();
    }
    private static void write(ServerPlayer p,JsonArray events) {
        CompoundTag root=p.getPersistentData(),tag=root.getCompound("PlayerPersisted");
        tag.putString(KEY,events.toString());root.put("PlayerPersisted",tag);
    }
    boolean canQueue(ServerPlayer p) {
        if(!enabled()) return true;
        String uid=p.getGameProfile().getName();
        // The authenticated server-side profile supplies identity, never the packet's day/id.
        try { return IdentityRules.validUid(uid) && IdentityRules.offlineUuid(uid).equals(p.getUUID()) && pending(p).size()<10000; }
        catch(RuntimeException invalidQueue) { return false; }
    }
    void enqueue(ServerPlayer p,DailyTaskState state,TaskCatalog.Definition task) {
        if(!enabled()) return;
        JsonArray events=pending(p); JsonObject e=new JsonObject();
        e.addProperty("uid",Long.parseLong(p.getGameProfile().getName()));
        e.addProperty("day",state.day().toString());e.addProperty("taskId",task.id());e.addProperty("hard",task.hard());
        events.add(e);write(p,events);
        // Caller saves this with the claim bit and inventory BEFORE pump can see it.
    }
    void pump(ServerPlayer p) {
        if(!enabled() || busy.contains(p.getUUID())) return;
        JsonArray events;
        try { events=pending(p); } catch(RuntimeException invalid) { return; }
        if(events.isEmpty()) return;
        try { ((PlayerListSaveInvoker)server.getPlayerList()).muxi$savePlayer(p); }
        catch(RuntimeException notSaved) { return; }
        String event=events.get(0).toString();UUID uuid=p.getUUID();busy.add(uuid);
        worker.submit(()->{
            boolean success=false;
            try {
                var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json").header("x-muxi-server-key",key)
                    .POST(HttpRequest.BodyPublishers.ofString(event)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                success=response.statusCode()==200 && JsonParser.parseString(response.body()).getAsJsonObject().get("ok").getAsBoolean();
            } catch(Exception ignored) { /* Keep durable event, including an unknown commit outcome. */ }
            final boolean ack=success;
            server.execute(()->{
                busy.remove(uuid);
                if(!ack || closed) return;
                ServerPlayer current=server.getPlayerList().getPlayer(uuid);
                if(current==null) return; // Logout/restart keeps NBT pending until next login.
                try {
                    JsonArray queue=pending(current);
                    if(!queue.isEmpty() && queue.get(0).toString().equals(event)) {
                        queue.remove(0);write(current,queue);
                        ((PlayerListSaveInvoker)server.getPlayerList()).muxi$savePlayer(current);
                    }
                } catch(RuntimeException ignored) { /* A stale persisted ack only causes an idempotent replay. */ }
            });
        });
    }
    @Override public void close() { closed=true;worker.shutdownNow();busy.clear(); }
}
