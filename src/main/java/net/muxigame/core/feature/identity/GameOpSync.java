package net.muxigame.core.feature.identity;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.storage.LevelResource;
import net.muxigame.core.config.CoreConfig;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/** Uses the existing identity HTTP client and tick cadence; only the server thread touches ops/journal. */
public final class GameOpSync implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/op-sync");
    private static volatile GameOpSync active;
    private final HttpClient http;
    private final CoreConfig.OpSync config;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private record Reply(int status, String body) {}
    private OpSyncJournal journal;
    private boolean initialized, busy;
    private volatile boolean closed;
    private String nextPage;
    private long requestedAfter;
    public GameOpSync(HttpClient http, CoreConfig.OpSync config) { this.http=http; this.config=config; active=this; }
    public static void requireLocalMutationReady(MinecraftServer server) throws CommandSyntaxException {
        GameOpSync sync=active;
        if (sync != null && !sync.closed && (sync.journal == null || sync.journal.hasPending()))
            throw new SimpleCommandExceptionType(Component.literal("OP 同步正在恢复或无法持久保存，请恢复日志后重试。")).create();
    }
    private HttpRequest.Builder request(String suffix) {
        return HttpRequest.newBuilder(URI.create(config.endpoint()+suffix)).timeout(Duration.ofSeconds(6))
            .header("Accept","application/json").header("X-Muxi-Server-Key",config.serverKey());
    }
    private void fetch(String cursor) {
        busy=true;requestedAfter=Long.parseLong(cursor);
        try {
            http.sendAsync(request("?afterUid="+cursor).GET().build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response,error)->{
                    if (!closed) replies.add(new Reply(error==null?response.statusCode():0,
                        error==null && response.body().length()<=65536?response.body():""));
                });
        } catch (RuntimeException ignored) { replies.add(new Reply(0,"")); }
    }
    public void tick(MinecraftServer server, boolean refresh) {
        if (closed) return;
        if (!server.isSameThread()) throw new IllegalStateException("OP sync must run on the server thread");
        if (!initialized) {
            initialized=true;
            try { journal=new OpSyncJournal(server.getWorldPath(LevelResource.ROOT).resolve("muxi-op-sync-applied.json")); }
            catch (IOException ignored) { LOG.error("OP sync journal cannot be loaded; no grants will be replayed."); }
        }
        if (journal==null) return; // Corruption fails closed; never silently replay older website grants.
        try { journal.recover(nativeTarget(server)); }
        catch (IOException | RuntimeException ignored) {
            if (refresh) LOG.warn("OP sync recovery is pending; local OP management remains blocked until durable recovery.");
            return;
        }
        Reply reply;
        while ((reply=replies.poll())!=null) {
            if (reply.status()==-1) { busy=false;nextPage=reply.body().isEmpty()?null:reply.body();continue; }
            if (reply.status()!=200) { busy=false; LOG.warn("OP sync feed failed (HTTP {}); retry on next identity refresh.",reply.status()); continue; }
            try {
                JsonObject feed=JsonParser.parseString(reply.body()).getAsJsonObject();
                JsonArray records=feed.getAsJsonArray("records");
                if (records.size()>100) throw new IllegalArgumentException();
                List<OpSyncJournal.Desired> desired=new ArrayList<>();
                long last=requestedAfter;
                for (JsonElement row:records) {
                    var d=OpSyncJournal.parse(row.getAsJsonObject());
                    long uid=Long.parseLong(d.uid());
                    if (uid<=last) throw new IllegalArgumentException();
                    last=uid; desired.add(d);
                }
                String cursor=feed.get("nextUid").isJsonNull()?null:feed.get("nextUid").getAsString();
                if (cursor!=null && (!IdentityRules.validUid(cursor) || desired.isEmpty() || !cursor.equals(desired.getLast().uid())))
                    throw new IllegalArgumentException();
                List<JsonObject> acknowledgements=new ArrayList<>();
                for (var d:desired) {
                    JsonObject ack=new JsonObject(); ack.addProperty("uid",d.uid());ack.addProperty("revision",d.revision());
                    try {
                        int observed=journal.consume(d, nativeTarget(server));
                        ack.addProperty("applied",true);ack.addProperty("observedLevel",observed);ack.add("error",JsonNull.INSTANCE);
                    } catch (IOException | RuntimeException ignored) {
                        ack.addProperty("applied",false);ack.add("observedLevel",JsonNull.INSTANCE);ack.addProperty("error","apply_failed");
                        LOG.warn("OP sync for UID {} version {} could not be durably applied.",d.uid(),d.revision());
                    }
                    acknowledgements.add(ack);
                }
                // Sequential bounded acknowledgements. Unknown outcomes retry from durable website snapshots.
                CompletableFuture<Void> sends=CompletableFuture.completedFuture(null);
                for (JsonObject ack:acknowledgements) {
                    sends=sends.thenCompose(v->{
                        if (closed) return CompletableFuture.completedFuture(null);
                        return http.sendAsync(request("ack").header("Content-Type","application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(ack.toString())).build(),HttpResponse.BodyHandlers.discarding())
                            .handle((response,error)->null);
                    });
                }
                final String page=cursor;
                sends.whenComplete((v,e)->{ if(!closed) replies.add(new Reply(-1,page==null?"":page)); });
            } catch (RuntimeException ignored) {
                busy=false;
                LOG.warn("Rejected invalid OP sync feed; existing game permissions retained.");
            }
        }
        if (nextPage!=null) { String cursor=nextPage;nextPage=null;fetch(cursor); }
        else if (refresh && !busy) { reportOnline(server);fetch("0"); }
    }
    private void reportOnline(MinecraftServer server) {
        JsonArray batch = new JsonArray();
        for (var player:server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) continue;
            String uid=player.getGameProfile().getName();
            if (!IdentityRules.validUid(uid) || !IdentityRules.offlineUuid(uid).equals(player.getUUID())) continue;
            var d=new OpSyncJournal.Desired(uid,player.getUUID(),1,0);
            JsonObject row=new JsonObject();row.addProperty("uid",uid);
            row.addProperty("offlineUuid",player.getUUID().toString());row.addProperty("level",nativeTarget(server).level(d));
            batch.add(row);
            if (batch.size()==100) { sendObservations(batch);batch=new JsonArray(); }
        }
        if (!batch.isEmpty()) sendObservations(batch);
    }
    private void sendObservations(JsonArray records) {
        try {
            http.sendAsync(request("observations").header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(records.toString())).build(),HttpResponse.BodyHandlers.discarding())
                .exceptionally(error->null);
        } catch (RuntimeException ignored) { /* Next identity refresh retries read-only observations. */ }
    }
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("muxiop").requires(OpCommandOrigin::allowed)
            .then(Commands.argument("player",GameProfileArgument.gameProfile())
                .then(Commands.argument("level",IntegerArgumentType.integer(0,4)).executes(context->{
                    var source=context.getSource();
                    OpCommandOrigin.require(source);
                    var profiles=GameProfileArgument.getGameProfiles(context,"player");
                    if (profiles.size()!=1) {source.sendFailure(Component.literal("请指定一个已知 UID。"));return 0;}
                    var profile=profiles.iterator().next();
                    if (!IdentityRules.validUid(profile.getName()) || !IdentityRules.offlineUuid(profile.getName()).equals(profile.getId())) {
                        source.sendFailure(Component.literal("玩家必须是稳定的 UID 身份。"));return 0;
                    }
                    int level=IntegerArgumentType.getInteger(context,"level");
                    try {
                        nativeTarget(source.getServer()).setLevel(new OpSyncJournal.Desired(profile.getName(),profile.getId(),1,level));
                        LOG.info("In-game OP changed by {}: UID {} -> level {}; website desired record unchanged.",
                            source.getTextName(),profile.getName(),level);
                        source.sendSuccess(()->Component.literal("UID "+profile.getName()+" 游戏 OP 设为 "+level+"；官网设定不变。"),true);
                        return 1;
                    } catch (IOException ignored) {source.sendFailure(Component.literal("无法持久保存游戏 OP，请检查服务端磁盘。"));return 0;}
                }))));
    }
    public static OpSyncJournal.Target nativeTarget(MinecraftServer server) {
        return new OpSyncJournal.Target() {
            @Override public void setLevel(OpSyncJournal.Desired d) throws IOException {
                if (!server.isSameThread()) throw new IllegalStateException("OP mutation off server thread");
                var list=server.getPlayerList();var ops=list.getOps();
                GameProfile profile=new GameProfile(d.uuid(),d.uid());
                var old=ops.get(profile);
                if (d.level()==0) ops.remove(profile);
                else ops.add(new ServerOpListEntry(profile,d.level(),old!=null && old.getBypassesPlayerLimit()));
                try {
                    ops.save(); // StoredUserList.add/remove swallows errors; explicit save must succeed.
                    try (var file=FileChannel.open(ops.getFile().toPath(),StandardOpenOption.WRITE)) { file.force(true); }
                } catch (IOException error) {
                    // A failed save must not leave an apparently successful in-memory observation.
                    if (old==null) ops.remove(profile); else ops.add(old);
                    throw error;
                }
                var player=list.getPlayer(d.uuid());
                if (player!=null) { list.sendPlayerPermissionLevel(player);server.getCommands().sendCommands(player); }
            }
            @Override public int level(OpSyncJournal.Desired d) {
                var entry=server.getPlayerList().getOps().get(new GameProfile(d.uuid(),d.uid()));
                return entry==null?0:entry.getLevel();
            }
        };
    }
    @Override public void close() { closed=true;replies.clear();if(active==this)active=null; }
}
