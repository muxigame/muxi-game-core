package net.muxigame.core.feature.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.minigames.TrustedAccounts;
import net.neoforged.neoforge.network.PacketDistributor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only an issuer-verified proof binds account trust to the exact online listener. */
public final class TerminalPassportServer {
    private static final ConcurrentHashMap<ServerGamePacketListenerImpl,Session> sessions=new ConcurrentHashMap<>();
    private static CoreConfig.TerminalSso config;
    private static HttpClient http;
    private static final class Session {
        final ServerPlayer player;
        final String uid, id=UUID.randomUUID().toString();
        final AtomicBoolean pending=new AtomicBoolean();
        volatile long last;
        Session(ServerPlayer player){this.player=player;this.uid=player.getGameProfile().getName();}
    }
    private TerminalPassportServer() {}
    static synchronized void configure(CoreConfig.TerminalSso value){
        close();
        config=value;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static void admit(ServerPlayer player){
        // This records a claim, NOT authentication and NOT any game admission or OP authority.
        if(config!=null && config.enabled() && player.connection!=null && player.connection.isAcceptingMessages()
            && player.server.getPlayerList().getPlayer(player.getUUID())==player
            && TerminalSsoIdentity.consistent(player.getGameProfile().getName(),player.getUUID()))
            sessions.put(player.connection,new Session(player));
    }
    private static boolean live(ServerPlayer player,ServerGamePacketListenerImpl listener,Session session){
        return player.connection==listener && listener.isAcceptingMessages() && sessions.get(listener)==session
            && player.server.getPlayerList().getPlayer(player.getUUID())==player
            && session.uid.equals(player.getGameProfile().getName())
            && TerminalSsoIdentity.consistent(session.uid,player.getUUID());
    }
    public static void request(ServerPlayer player,TerminalPassportNetwork.Request packet){
        var listener=player.connection;
        var session=listener==null?null:sessions.get(listener);
        var authority=config;
        var transport=http;
        if(session==null || authority==null || !authority.enabled() || transport==null || !live(player,listener,session)
            || !packet.requestId().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            || !packet.proof().matches("[A-Za-z0-9_-]{43}")) {
            reply(player,packet.requestId(),"");return;
        }
        long now=System.nanoTime();
        if((session.last!=0 && now-session.last<2_000_000_000L) || !session.pending.compareAndSet(false,true)){
            reply(player,packet.requestId(),"");return;
        }
        session.last=now;
        JsonObject body=new JsonObject();
        body.addProperty("uid",Long.parseLong(session.uid));body.addProperty("proof",packet.proof());
        body.addProperty("requestId",packet.requestId());body.addProperty("gameSession",session.id);
        try {
            transport.sendAsync(post(authority,"terminal-ticket",body),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response,error)->player.server.execute(()->{
                    session.pending.set(false);
                    if(!live(player,listener,session))return;
                    // The HTTP timeout cannot bound time waiting in the server thread's queue.
                    if(System.nanoTime()-now>=30_000_000_000L){reply(player,packet.requestId(),"");return;}
                    String ticket="";
                    if(error==null && response.statusCode()==200 && response.body().length()<=4096){
                        try {
                            JsonObject result=JsonParser.parseString(response.body()).getAsJsonObject();
                            String value=result.get("ticket").getAsString();
                            if(value.matches("[A-Za-z0-9_-]{43}")
                                && result.get("uid").isJsonPrimitive() && result.get("uid").getAsJsonPrimitive().isNumber()
                                && session.uid.equals(result.get("uid").getAsString())
                                && packet.requestId().equals(result.get("requestId").getAsString())
                                && session.id.equals(result.get("gameSession").getAsString())
                                && "30".equals(result.get("expiresInSeconds").getAsString())) {
                                // Shared social trust only. LoginGate/OP/roles remain unchanged.
                                TrustedAccounts.admitTerminal(player,Long.parseLong(session.uid));
                                if(live(player,listener,session) && TrustedAccounts.socialUid(player)==Long.parseLong(session.uid))ticket=value;
                                else TrustedAccounts.revoke(player);
                            }
                        }catch(RuntimeException | LinkageError ignored){}
                    }
                    reply(player,packet.requestId(),ticket);
                }));
        }catch(RuntimeException ignored){session.pending.set(false);reply(player,packet.requestId(),"");}
    }
    private static HttpRequest post(CoreConfig.TerminalSso authority,String route,JsonObject body){
        return HttpRequest.newBuilder(URI.create(authority.endpoint()).resolve(route))
            .timeout(Duration.ofSeconds(4)).header("X-Muxi-Server-Key",authority.serverKey())
            .header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
    }
    private static void reply(ServerPlayer player,String requestId,String ticket){
        if(player.connection!=null && player.connection.isAcceptingMessages())
            PacketDistributor.sendToPlayer(player,new TerminalPassportNetwork.Result(requestId,ticket));
    }
    private static CompletableFuture<?> revokeRemote(Session session,CoreConfig.TerminalSso authority,HttpClient transport){
        JsonObject body=new JsonObject();body.addProperty("uid",Long.parseLong(session.uid));body.addProperty("gameSession",session.id);
        return transport.sendAsync(post(authority,"terminal-disconnect",body),HttpResponse.BodyHandlers.discarding());
    }
    static void disconnect(ServerPlayer player){
        Session session=player.connection==null?null:sessions.remove(player.connection);
        // Shared revoke is connection-conditional; an old logout cannot clear a new listener.
        TrustedAccounts.revoke(player);
        var authority=config;var transport=http;
        if(session==null || authority==null || transport==null)return;
        try{revokeRemote(session,authority,transport);}catch(RuntimeException ignored){}
    }
    static synchronized void close(){
        var authority=config;var transport=http;
        var retiring=new ArrayList<>(sessions.values());
        sessions.clear();config=null;http=null;
        var revocations=new ArrayList<CompletableFuture<?>>();
        for(Session session:retiring){
            TrustedAccounts.revoke(session.player);
            if(authority!=null && transport!=null){
                try{revocations.add(revokeRemote(session,authority,transport));}catch(RuntimeException ignored){}
            }
        }
        if(transport!=null){
            // Bound cleanup; tickets expire independently in 30 seconds if the issuer is unreachable.
            try{CompletableFuture.allOf(revocations.toArray(CompletableFuture[]::new)).orTimeout(2,TimeUnit.SECONDS).join();}
            catch(RuntimeException ignored){}
            transport.shutdownNow();
        }
    }
}
