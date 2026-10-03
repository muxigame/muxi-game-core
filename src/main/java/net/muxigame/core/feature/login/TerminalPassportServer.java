package net.muxigame.core.feature.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.minigames.TrustedAccounts;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Auth verifies the original access token; this server sees only connection correlation and UID. */
public final class TerminalPassportServer {
    private static final ConcurrentHashMap<ServerGamePacketListenerImpl,Session> sessions=new ConcurrentHashMap<>();
    private static CoreConfig.TerminalSso config;
    private static HttpClient http;
    private static final class Session {
        final ServerPlayer player;
        final String uid,id=UUID.randomUUID().toString();
        final AtomicBoolean pending=new AtomicBoolean(),checking=new AtomicBoolean();
        volatile String request="";
        volatile long last,expires,nextCheck,authorizationGeneration;
        volatile boolean authenticated;
        Session(ServerPlayer player){this.player=player;uid=player.getGameProfile().getName();}
    }
    private TerminalPassportServer() {}
    static synchronized void configure(CoreConfig.TerminalSso value){
        close();config=value;http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static void admit(ServerPlayer player){
        if(config!=null && config.enabled() && player.connection!=null && player.connection.isAcceptingMessages()
            && player.server.getPlayerList().getPlayer(player.getUUID())==player
            && TerminalSsoIdentity.consistent(player.getGameProfile().getName(),player.getUUID()))sessions.put(player.connection,new Session(player));
    }
    private static boolean live(ServerPlayer player,ServerGamePacketListenerImpl listener,Session session){
        return player.connection==listener && listener.isAcceptingMessages() && sessions.get(listener)==session
            && player.server.getPlayerList().getPlayer(player.getUUID())==player && session.uid.equals(player.getGameProfile().getName())
            && TerminalSsoIdentity.consistent(session.uid,player.getUUID());
    }
    private static boolean uuid(String value){return value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    public static void request(ServerPlayer player,TerminalPassportNetwork.Request packet){
        var listener=player.connection;var session=listener==null?null:sessions.get(listener);var authority=config;var transport=http;
        if(session==null || authority==null || !authority.enabled() || transport==null || !live(player,listener,session) || !uuid(packet.requestId())){
            reply(player,packet.requestId(),"",0,0);return;
        }
        if(packet.action()==2 && packet.gameSession().equals(session.id)){
            clearTerminal(session);
            try{revokeRemote(session,authority,transport,"revoke");}catch(RuntimeException ignored){}
            return;
        }
        long now=System.nanoTime();
        boolean create=packet.action()==0 && packet.gameSession().isEmpty();
        boolean claim=packet.action()==1 && packet.gameSession().equals(session.id) && packet.requestId().equals(session.request) && now<session.expires;
        if((!create && !claim) || (create && session.last!=0 && now-session.last<2_000_000_000L) || !session.pending.compareAndSet(false,true)){
            reply(player,packet.requestId(),"",0,0);return;
        }
        if(create){session.last=now;session.expires=now+30_000_000_000L;session.request=packet.requestId();}
        JsonObject body=body(session);body.addProperty("requestId",packet.requestId());
        try{
            transport.sendAsync(post(authority,create?"create":"claim",body),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response,error)->player.server.execute(()->{
                    session.pending.set(false);
                    if(!live(player,listener,session) || !session.request.equals(packet.requestId()))return;
                    if(System.nanoTime()>=session.expires){session.request="";reply(player,packet.requestId(),"",0,0);return;}
                    try{
                        if(error!=null || response.statusCode()!=200 || response.body().length()>4096)throw new IllegalStateException();
                        var result=JsonParser.parseString(response.body()).getAsJsonObject();
                        if(!session.uid.equals(result.get("uid").getAsString()) || !session.id.equals(result.get("gameSession").getAsString())
                            || !packet.requestId().equals(result.get("requestId").getAsString()))throw new IllegalStateException();
                        if(create){
                            if(result.get("expiresInSeconds").getAsInt()!=30)throw new IllegalStateException();
                            reply(player,packet.requestId(),session.id,Long.parseLong(session.uid),1);
                        }else{
                            TrustedAccounts.admitTerminal(player,Long.parseLong(session.uid));
                            session.authorizationGeneration++;session.authenticated=true;session.nextCheck=System.nanoTime()+15_000_000_000L;session.request="";
                            reply(player,packet.requestId(),session.id,Long.parseLong(session.uid),2);
                        }
                    }catch(RuntimeException | LinkageError ignored){session.request="";reply(player,packet.requestId(),"",0,0);}
                }));
        }catch(RuntimeException ignored){session.pending.set(false);session.request="";reply(player,packet.requestId(),"",0,0);}
    }
    static void tick(ServerTickEvent.Post event){
        var authority=config;var transport=http;if(authority==null || transport==null)return;
        long now=System.nanoTime();
        for(var entry:sessions.entrySet()){
            var listener=entry.getKey();var session=entry.getValue();var player=session.player;
            if(!session.authenticated || now<session.nextCheck || !live(player,listener,session) || !session.checking.compareAndSet(false,true))continue;
            session.nextCheck=now+15_000_000_000L;long authorizationGeneration=session.authorizationGeneration;
            try{transport.sendAsync(post(authority,"status",body(session)),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response,error)->player.server.execute(()->{
                    session.checking.set(false);if(!live(player,listener,session) || session.authorizationGeneration!=authorizationGeneration)return;
                    try{
                        if(error!=null || response.statusCode()!=200 || response.body().length()>4096)throw new IllegalStateException();
                        var value=JsonParser.parseString(response.body()).getAsJsonObject();
                        if(!session.uid.equals(value.get("uid").getAsString()) || !session.id.equals(value.get("gameSession").getAsString())
                            || !value.get("authenticated").getAsBoolean())throw new IllegalStateException();
                    }catch(RuntimeException ignored){clearTerminal(session);}
                }));}catch(RuntimeException ignored){session.checking.set(false);clearTerminal(session);}
        }
    }
    private static JsonObject body(Session session){var body=new JsonObject();body.addProperty("uid",Long.parseLong(session.uid));body.addProperty("gameSession",session.id);return body;}
    private static HttpRequest post(CoreConfig.TerminalSso authority,String route,JsonObject body){
        return HttpRequest.newBuilder(URI.create(authority.endpoint()).resolve("terminal-context/"+route)).timeout(Duration.ofSeconds(4))
            .header("X-Muxi-Server-Key",authority.serverKey()).header("Content-Type","application/json").header("Accept","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
    }
    private static void reply(ServerPlayer player,String id,String session,long uid,int status){
        if(player.connection!=null && player.connection.isAcceptingMessages())PacketDistributor.sendToPlayer(player,new TerminalPassportNetwork.Result(id,session,uid,status));
    }
    private static void clearTerminal(Session session){session.authorizationGeneration++;session.authenticated=false;session.request="";TrustedAccounts.revokeTerminal(session.player);}
    private static CompletableFuture<?> revokeRemote(Session session,CoreConfig.TerminalSso authority,HttpClient transport,String route){
        return transport.sendAsync(post(authority,route,body(session)),HttpResponse.BodyHandlers.discarding());
    }
    static void disconnect(ServerPlayer player){
        var session=player.connection==null?null:sessions.remove(player.connection);TrustedAccounts.revokeTerminal(player);
        var authority=config;var transport=http;if(session==null || authority==null || transport==null)return;
        try{revokeRemote(session,authority,transport,"disconnect");}catch(RuntimeException ignored){}
    }
    static synchronized void close(){
        var authority=config;var transport=http;var retiring=new ArrayList<>(sessions.values());sessions.clear();config=null;http=null;
        var revocations=new ArrayList<CompletableFuture<?>>();
        for(var session:retiring){clearTerminal(session);if(authority!=null && transport!=null)try{revocations.add(revokeRemote(session,authority,transport,"disconnect"));}catch(RuntimeException ignored){}}
        if(transport!=null){try{CompletableFuture.allOf(revocations.toArray(CompletableFuture[]::new)).orTimeout(2,TimeUnit.SECONDS).join();}catch(RuntimeException ignored){}transport.shutdownNow();}
    }
}
