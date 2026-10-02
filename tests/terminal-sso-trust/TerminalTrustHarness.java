package net.muxigame.core.feature.login;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.minigames.TrustedAccounts;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Actual Core and task2 trust code, isolated Minecraft transport fixtures, no production keys. */
public final class TerminalTrustHarness {
    static int assertions;
    static final String KEY="synthetic-terminal-server-key-32-long";
    static final String PROOF="p".repeat(43),TICKET="t".repeat(43);
    static final String ENDPOINT="https://account.muxigame.com/api/internal/minecraft/";
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);assertions++;}
    static boolean social(ServerPlayer p){try{TrustedAccounts.socialUid(p);return true;}catch(IllegalArgumentException absent){return false;}}
    static boolean join(ServerPlayer p){try{TrustedAccounts.uid(p);return true;}catch(IllegalArgumentException absent){return false;}}
    static ServerPlayer player(MinecraftServer server,long uid){return new ServerPlayer(server,Long.toString(uid),IdentityRules.offlineUuid(Long.toString(uid)));}
    record Response<T>(HttpRequest request,int statusCode,T body) implements HttpResponse<T>{
        public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}
        public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
        public Optional<SSLSession> sslSession(){return Optional.empty();}
        public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
    }
    static final class Transport extends HttpClient {
        final List<HttpRequest> calls=new ArrayList<>();
        final Queue<CompletableFuture<HttpResponse<String>>> pending=new ArrayDeque<>();
        public Optional<java.net.CookieHandler> cookieHandler(){return Optional.empty();}
        public Optional<Duration> connectTimeout(){return Optional.of(Duration.ofSeconds(1));}
        public Redirect followRedirects(){return Redirect.NEVER;}
        public Optional<ProxySelector> proxy(){return Optional.empty();}
        public SSLContext sslContext(){try{return SSLContext.getDefault();}catch(Exception e){throw new AssertionError(e);}}
        public SSLParameters sslParameters(){return new SSLParameters();}
        public Optional<java.net.Authenticator> authenticator(){return Optional.empty();}
        public Version version(){return Version.HTTP_1_1;}public Optional<Executor> executor(){return Optional.empty();}
        public <T> HttpResponse<T> send(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
        @SuppressWarnings("unchecked") public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){
            calls.add(r);
            if(r.uri().getPath().endsWith("terminal-disconnect"))return CompletableFuture.completedFuture(new Response<>(r,200,null));
            CompletableFuture<HttpResponse<String>> f=new CompletableFuture<>();pending.add(f);return (CompletableFuture<HttpResponse<T>>)(Object)f;
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return sendAsync(r,h);}
        public void shutdownNow(){}
    }
    static class Fixture {
        final MinecraftServer server=new MinecraftServer();
        final ServerPlayer player=player(server,10000);final Transport http=new Transport();
        final TerminalPassportNetwork.Request request=new TerminalPassportNetwork.Request(UUID.randomUUID().toString(),PROOF);
        Fixture()throws Exception{
            TerminalPassportServer.close();
            Field authority=TerminalPassportServer.class.getDeclaredField("config");authority.setAccessible(true);
            authority.set(null,new CoreConfig.TerminalSso(true,ENDPOINT,KEY));
            Field transport=TerminalPassportServer.class.getDeclaredField("http");transport.setAccessible(true);
            transport.set(null,http);
            PacketDistributor.deliveries.clear();TerminalPassportServer.admit(player);
        }
        void request(){TerminalPassportServer.request(player,request);}
        JsonObject result()throws Exception{
            Field sessions=TerminalPassportServer.class.getDeclaredField("sessions");sessions.setAccessible(true);
            Object session=((Map<?,?>)sessions.get(null)).get(player.connection);
            Field id=session.getClass().getDeclaredField("id");id.setAccessible(true);
            JsonObject result=new JsonObject();result.addProperty("uid",10000);result.addProperty("requestId",request.requestId());
            result.addProperty("gameSession",(String)id.get(session));result.addProperty("expiresInSeconds",30);result.addProperty("ticket",TICKET);return result;
        }
        void respond(int status,String body){http.pending.remove().complete(new Response<>(http.calls.get(http.calls.size()-1),status,body));}
        void finish(){server.flush();}
        void close(){TerminalPassportServer.close();}
    }
    static void unit()throws Exception{
        Fixture f=new Fixture();check(!social(f.player),"numeric profile never authenticates");check(!join(f.player),"join gate remains off");
        f.request();check(f.http.pending.size()==1,"actual async auth request");
        HttpRequest sent=f.http.calls.get(0);check(sent.uri().toString().equals(ENDPOINT+"terminal-ticket"),"dedicated endpoint");
        check(sent.headers().firstValue("X-Muxi-Server-Key").orElse("").equals(KEY),"dedicated key only");
        f.respond(200,f.result().toString());check(!social(f.player),"async callback must await game thread");f.finish();
        check(TrustedAccounts.socialUid(f.player)==10000,"verified social UID binds current listener");check(!join(f.player),"SSO never grants settlement admission");
        check(PacketDistributor.deliveries.get(0).packet().ticket().equals(TICKET),"native result carries valid ticket");
        f.close();check(!social(f.player),"server stop revokes social trust");
        check(f.http.calls.stream().anyMatch(x->x.uri().getPath().endsWith("terminal-disconnect")),"server stop revokes issuer ticket");

        for(String field:List.of("uid","requestId","gameSession","ticket","expiresInSeconds")){
            for(boolean missing:List.of(false,true)){
                f=new Fixture();f.request();JsonObject body=f.result();
                if(missing)body.remove(field);else body.addProperty(field,field.equals("uid")?"10001":"invalid");
                f.respond(200,body.toString());f.finish();check(!social(f.player),"malformed/unmatched "+field+" cannot authenticate");
                check(PacketDistributor.deliveries.get(0).packet().ticket().isEmpty(),"malformed result fails normal login fallback");f.close();
            }
        }
        for(int status:List.of(401,403,503,302)){
            f=new Fixture();f.request();f.respond(status,f.result().toString());f.finish();
            check(!social(f.player),"issuer rejection/redirect cannot authenticate");check(!join(f.player),"issuer failure never opens join gate");f.close();
        }
        f=new Fixture();f.request();f.http.pending.remove().completeExceptionally(new IOException("synthetic outage"));f.finish();
        check(!social(f.player),"network failure cannot authenticate");f.close();

        f=new Fixture();f.request();f.respond(200,f.result().toString());
        Thread.sleep(30_010);f.finish(); // Real elapsed main-thread queue time, not a modified production clock.
        check(!social(f.player),"expired queued issuer ticket cannot establish trust");
        check(PacketDistributor.deliveries.get(0).packet().ticket().isEmpty(),"expired queued ticket is not delivered");f.close();

        f=new Fixture();f.request();String delayed=f.result().toString();
        TerminalPassportServer.disconnect(f.player);ServerPlayer renewed=player(f.server,10000);TerminalPassportServer.admit(renewed);
        f.respond(200,delayed);f.finish();check(!social(renewed),"reconnect never inherits late response");
        check(PacketDistributor.deliveries.isEmpty(),"old response never delivered to new connection");
        TerminalPassportNetwork.Request currentRequest=new TerminalPassportNetwork.Request(UUID.randomUUID().toString(),PROOF);
        TerminalPassportServer.request(renewed,currentRequest);
        Field sessionField=TerminalPassportServer.class.getDeclaredField("sessions");sessionField.setAccessible(true);
        Object renewedSession=((Map<?,?>)sessionField.get(null)).get(renewed.connection);Field id=renewedSession.getClass().getDeclaredField("id");id.setAccessible(true);
        JsonObject current=new JsonObject();current.addProperty("uid",10000);current.addProperty("ticket",TICKET);current.addProperty("requestId",currentRequest.requestId());
        current.addProperty("gameSession",(String)id.get(renewedSession));current.addProperty("expiresInSeconds",30);
        f.respond(200,current.toString());f.finish();check(social(renewed),"new connection independently authenticates");
        TerminalPassportServer.disconnect(f.player);check(social(renewed),"old logout cannot revoke new connection");
        TerminalPassportServer.disconnect(renewed);check(!social(renewed),"current logout revokes binding");f.close();

        f=new Fixture();f.request();delayed=f.result().toString();f.player.connection.online=false;
        f.respond(200,delayed);f.finish();check(!social(f.player),"offline listener cannot authenticate");check(PacketDistributor.deliveries.isEmpty(),"offline result discarded");f.close();
        f=new Fixture();f.request();delayed=f.result().toString();player(f.server,10000);
        f.respond(200,delayed);f.finish();check(!social(f.player),"replaced current player cannot authenticate");f.close();
        f=new Fixture();f.player.profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"10000");f.request();
        check(f.http.calls.isEmpty(),"inconsistent UUID cannot request trust");f.close();
        f=new Fixture();TerminalPassportServer.request(f.player,new TerminalPassportNetwork.Request("-".repeat(36),PROOF));
        check(f.http.calls.isEmpty(),"invalid request UUID rejected");f.close();
        f=new Fixture();f.request();f.request();check(f.http.calls.size()==1,"pending request throttled");f.close();

        check(!CoreConfig.parse("{}").terminalSso().enabled(),"SSO off by default");
        String enabled="{\"features\":{\"terminalSso\":{\"enabled\":true,\"serverKey\":\""+KEY+"\"}}}";
        CoreConfig config=CoreConfig.parse(enabled);check(config.terminalSso().enabled(),"dedicated SSO config supported");
        check(!config.login().enabled()&&!config.opSync().enabled(),"SSO does not enable LoginGate/OP");check(!config.toString().contains(KEY),"config redacts key");
        try{CoreConfig.parse(enabled.replace("\""+KEY+"\"","12345678901234567890123456789012"));throw new AssertionError("Expected string key");}
        catch(IllegalArgumentException expected){check(true,"dedicated key must have string type");}
        for(String name:List.of("identity","login","opSync")){
            String reused=enabled.replace("{\"terminalSso\"","{\""+name+"\":{\"enabled\":false,\"serverKey\":\""+KEY+"\"},\"terminalSso\"");
            try{CoreConfig.parse(reused);throw new AssertionError("Expected purpose-key rejection");}
            catch(IllegalArgumentException expected){check(!expected.getMessage().contains(KEY),"duplicate purpose key rejected with redacted error");}
        }
        System.out.println("Dedicated terminal connection assertions: "+assertions+" passed");
    }
    static void emit(JsonObject result){System.out.println(result);System.out.flush();}
    static void serve()throws Exception{
        MinecraftServer server=new MinecraftServer();ServerPlayer current=null;
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,java.nio.charset.StandardCharsets.UTF_8));
        try{
            String line;
            while((line=input.readLine())!=null){
                JsonObject command=JsonParser.parseString(line).getAsJsonObject();String op=command.get("op").getAsString();JsonObject out=new JsonObject();
                if(op.equals("configure")){
                    TerminalPassportServer.configure(new CoreConfig.TerminalSso(true,command.get("endpoint").getAsString(),command.get("key").getAsString()));
                    current=player(server,command.get("uid").getAsLong());TerminalPassportServer.admit(current);out.addProperty("trusted",social(current));out.addProperty("joinAdmission",join(current));
                    out.addProperty("computer",System.getenv("COMPUTERNAME"));
                }else if(op.equals("request")){
                    int before=PacketDistributor.deliveries.size();
                    TerminalPassportServer.request(current,new TerminalPassportNetwork.Request(command.get("requestId").getAsString(),command.get("proof").getAsString()));
                    server.until(()->PacketDistributor.deliveries.size()>before);
                    out.addProperty("ticket",PacketDistributor.deliveries.get(before).packet().ticket());out.addProperty("trusted",social(current));out.addProperty("joinAdmission",join(current));
                    if(social(current))out.addProperty("uid",TrustedAccounts.socialUid(current));
                }else if(op.equals("disconnect")){
                    TerminalPassportServer.disconnect(current);out.addProperty("trusted",social(current));out.addProperty("joinAdmission",join(current));
                }else if(op.equals("reconnect")){
                    long uid=Long.parseLong(current.getGameProfile().getName());TerminalPassportServer.disconnect(current);current.connection.online=false;
                    current=player(server,uid);TerminalPassportServer.admit(current);out.addProperty("trusted",social(current));out.addProperty("joinAdmission",join(current));
                }else if(op.equals("close")){TerminalPassportServer.close();out.addProperty("trusted",social(current));emit(out);return;}
                else throw new AssertionError("Unknown isolated test command");
                emit(out);
            }
        }finally{TerminalPassportServer.close();}
    }
    public static void main(String[] args)throws Exception{if(args.length>0&&args[0].equals("--serve"))serve();else unit();}
}
