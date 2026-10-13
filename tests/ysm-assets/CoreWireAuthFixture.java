package net.muxigame.core.feature.login;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.core.feature.ysmassets.*;
import net.muxigame.minigames.TrustedAccounts;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Isolated loopback driver. Actual Auth sources; explicitly synthetic issuer responses and MC listeners. */
public final class CoreWireAuthFixture {
    static final String SID="fixture-core-131",WEB="fixture-web-131",MARKER="isolated-wire-control-only";
    static final byte[] WEBKEY="fixture-web-resolver-key-not-production-32bytes".getBytes(StandardCharsets.UTF_8);
    static final MinecraftServer server=new MinecraftServer();static final Map<String,ServerPlayer> players=new HashMap<>();
    static final TerminalTrustHarness.Transport transport=new TerminalTrustHarness.Transport();static volatile boolean stop;
    static ServerPlayer newPlayer(String uid){var p=new ServerPlayer(server,uid,IdentityRules.offlineUuid(uid));players.put(uid,p);TerminalPassportServer.admit(p);return p;}
    static JsonObject result(ServerPlayer p,String id)throws Exception{
        var o=new JsonObject();o.addProperty("uid",p.getGameProfile().getName());o.addProperty("gameSession",TerminalTrustHarness.field(TerminalTrustHarness.session(p),"id").toString());o.addProperty("requestId",id);o.addProperty("expiresInSeconds",30);return o;
    }
    static void reply(JsonObject body){transport.pending.remove().complete(new TerminalTrustHarness.Response<>(transport.calls.getLast(),200,body.toString()));server.flush();}
    static void authorize(ServerPlayer p)throws Exception{
        var sess=TerminalTrustHarness.session(p);TerminalTrustHarness.set(sess,"last",0L);String id=UUID.randomUUID().toString();TerminalPassportServer.request(p,new TerminalPassportNetwork.Request(id,"",0));reply(result(p,id));
        TerminalPassportServer.request(p,new TerminalPassportNetwork.Request(id,TerminalTrustHarness.field(sess,"id").toString(),1));reply(result(p,id));
    }
    static boolean social(ServerPlayer p){try{TrustedAccounts.socialUid(p);return true;}catch(IllegalArgumentException e){return false;}}
    static JsonObject state(ServerPlayer p){
        var out=new JsonObject();out.addProperty("uid",p.getGameProfile().getName());out.addProperty("social",social(p));out.addProperty("runtimeActive",false);
        try{out.add("claim",new Gson().toJsonTree(YsmAuthBindings.claimFor(p)));}catch(IllegalArgumentException e){out.add("claim",JsonNull.INSTANCE);}return out;
    }
    static JsonObject control(JsonObject body)throws Exception{
        String uid=body.has("uid")?body.get("uid").getAsString():"10000";if(!Set.of("10000","10001").contains(uid))throw new IllegalArgumentException();var p=players.get(uid);
        switch(body.get("action").getAsString()){
            case "state":break;
            case "authorize":authorize(p);break;
            case "revoke":TerminalPassportServer.request(p,new TerminalPassportNetwork.Request(UUID.randomUUID().toString(),TerminalTrustHarness.field(TerminalTrustHarness.session(p),"id").toString(),2));break;
            case "expire":
                for(var other:players.values())TerminalTrustHarness.set(TerminalTrustHarness.session(other),"nextCheck",Long.MAX_VALUE);
                TerminalTrustHarness.set(TerminalTrustHarness.session(p),"nextCheck",0L);TerminalPassportServer.tick(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post());var expired=result(p,UUID.randomUUID().toString());expired.addProperty("authenticated",false);reply(expired);break;
            case "join-source":TrustedAccounts.admit(p);YsmAuthBindings.joinVerified(p);break;
            case "logout":TerminalPassportServer.disconnect(p);p.connection.online=false;break;
            case "reconnect":TerminalPassportServer.disconnect(p);p.connection.online=false;p=newPlayer(uid);authorize(p);break;
            case "stop":stop=true;break;
            default:throw new IllegalArgumentException();
        }
        return state(p);
    }
    static void write(HttpExchange e,int code,String data)throws java.io.IOException{byte[] raw=data.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(code,raw.length);e.getResponseBody().write(raw);e.close();}
    public static void main(String[] args)throws Exception{
        TerminalPassportServer.close();TerminalTrustHarness.setStatic("config",new CoreConfig.TerminalSso(true,"https://account.muxigame.com/api/internal/minecraft/","fixture-original-issuer-key-not-production"));TerminalTrustHarness.setStatic("http",transport);
        YsmAuthBindings.attach(server,SID,new YsmAssetCoordinator.RuntimePort(){});var a=newPlayer("10000");var b=newPlayer("10001");authorize(a);authorize(b);
        var credential=new YsmMachineAuthority.Credential(WEB,"fixture-web-key",WEBKEY,Set.of("connection-resolve"),Set.of("ysm-core:"+SID));var authority=new YsmMachineAuthority("ysm-core:"+SID,k->k.equals(credential.keyId())?credential:null,new YsmMachineAuthority.MemoryReplayStore(),System::currentTimeMillis);
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var pool=Executors.newFixedThreadPool(3);http.setExecutor(pool);http.createContext(YsmCoreConnectionHandler.PATH,YsmCoreConnectionHandler.forIsolatedLoopback(server,authority));
        http.createContext("/control",e->{
            if(!e.getRemoteAddress().getAddress().isLoopbackAddress()||!MARKER.equals(e.getRequestHeaders().getFirst("X-Core-Wire-Test"))){write(e,403,"{}");return;}
            try{var body=JsonParser.parseString(new String(e.getRequestBody().readNBytes(4097),StandardCharsets.UTF_8)).getAsJsonObject();var future=new CompletableFuture<JsonObject>();server.execute(()->{try{future.complete(control(body));}catch(Exception error){future.completeExceptionally(error);}});write(e,200,future.get(3,TimeUnit.SECONDS).toString());}catch(Exception error){write(e,409,"{}");}
        });http.start();var ready=new JsonObject();ready.addProperty("port",http.getAddress().getPort());ready.addProperty("serverId",SID);ready.addProperty("syntheticIssuerAndMcListeners",true);ready.addProperty("productionActivated",false);System.out.println(ready);System.out.flush();
        try{long deadline=System.nanoTime()+180_000_000_000L;while(!stop&&System.nanoTime()<deadline){server.flush();Thread.sleep(5);}}
        finally{TerminalPassportServer.close();YsmAuthBindings.sourceStopped(YsmAuthBindings.Source.JOIN);http.stop(0);pool.shutdownNow();System.out.println("OWNED_FIXTURE_NORMAL_EXIT");}
    }
}
