package net.muxigame.core.feature.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.core.config.CoreConfig;
import net.neoforged.neoforge.network.PacketDistributor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account proof is verified by the issuer; a game profile alone never authorizes SSO. */
public final class TerminalPassportServer {
    private static final ConcurrentHashMap<ServerGamePacketListenerImpl,Session> sessions=new ConcurrentHashMap<>();
    private static CoreConfig.TerminalSso config;
    private static HttpClient http;
    private static final class Session {
        final String uid, id=UUID.randomUUID().toString();
        final AtomicBoolean pending=new AtomicBoolean();
        volatile long last;
        Session(String uid){this.uid=uid;}
    }
    private TerminalPassportServer() {}
    static synchronized void configure(CoreConfig.TerminalSso value){
        config=value;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static void admit(ServerPlayer player){
        // Record only the actual transport and consistent profile claim. No game access/privilege changes.
        if(config!=null && config.enabled() && TerminalSsoIdentity.consistent(player.getGameProfile().getName(),player.getUUID()))
            sessions.put(player.connection,new Session(player.getGameProfile().getName()));
    }
    public static void request(ServerPlayer player,TerminalPassportNetwork.Request packet){
        var listener=player.connection;
        var session=sessions.get(listener);
        if(session==null || !session.uid.equals(player.getGameProfile().getName())
            || !packet.requestId().matches("[0-9a-f-]{36}") || !packet.proof().matches("[A-Za-z0-9_-]{43}")) {
            reply(player,packet.requestId(),""); return;
        }
        long now=System.nanoTime();
        if((session.last!=0 && now-session.last<2_000_000_000L) || !session.pending.compareAndSet(false,true)){
            reply(player,packet.requestId(),"");return;
        }
        session.last=now;
        JsonObject body=new JsonObject();
        body.addProperty("uid",Long.parseLong(session.uid));
        body.addProperty("proof",packet.proof());body.addProperty("requestId",packet.requestId());body.addProperty("gameSession",session.id);
        try {
            http.sendAsync(post("terminal-ticket",body),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response,error)->player.server.execute(()->{
                    session.pending.set(false);
                    ServerPlayer current=player.server.getPlayerList().getPlayer(player.getUUID());
                    if(current==null || current.connection!=listener || sessions.get(listener)!=session) return;
                    String ticket="";
                    if(error==null && response.statusCode()==200){
                        try {
                            String value=JsonParser.parseString(response.body()).getAsJsonObject().get("ticket").getAsString();
                            if(value.matches("[A-Za-z0-9_-]{43}")) ticket=value;
                        } catch(RuntimeException ignored) {}
                    }
                    reply(current,packet.requestId(),ticket);
                }));
        } catch(RuntimeException ignored){session.pending.set(false);reply(player,packet.requestId(),"");}
    }
    private static HttpRequest post(String route,JsonObject body){
        return HttpRequest.newBuilder(URI.create(config.endpoint()).resolve("/api/internal/minecraft/"+route))
            .timeout(Duration.ofSeconds(4)).header("X-Muxi-Server-Key",config.serverKey())
            .header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
    }
    private static void reply(ServerPlayer player,String requestId,String ticket){
        PacketDistributor.sendToPlayer(player,new TerminalPassportNetwork.Result(requestId,ticket));
    }
    static void disconnect(ServerPlayer player){
        Session session=sessions.remove(player.connection);
        if(session==null || http==null) return;
        JsonObject body=new JsonObject();body.addProperty("uid",Long.parseLong(session.uid));body.addProperty("gameSession",session.id);
        try {http.sendAsync(post("terminal-disconnect",body),HttpResponse.BodyHandlers.discarding());}
        catch(RuntimeException ignored) {}
    }
    static synchronized void close(){
        sessions.clear();config=null;
        if(http!=null){http.shutdownNow();http=null;}
    }
}
