package net.muxigame.core.feature.login;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.minigames.TrustedAccounts;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Deterministic listener/HTTP fixtures run the actual server and account trust sources. */
public final class TerminalTrustHarness {
 static int assertions;
 static final String KEY="synthetic-terminal-server-key-32-long",ENDPOINT="https://account.muxigame.com/api/internal/minecraft/";
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);assertions++;}
 static boolean social(ServerPlayer p){try{TrustedAccounts.socialUid(p);return true;}catch(IllegalArgumentException absent){return false;}}
 static boolean join(ServerPlayer p){try{TrustedAccounts.uid(p);return true;}catch(IllegalArgumentException absent){return false;}}
 static ServerPlayer player(MinecraftServer s){return new ServerPlayer(s,"10000",IdentityRules.offlineUuid("10000"));}
 record Response<T>(HttpRequest request,int statusCode,T body) implements HttpResponse<T>{
  public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return HttpHeaders.of(Map.of(),(a,b)->true);}
  public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
 }
 static final class Transport extends HttpClient {
  final List<HttpRequest> calls=new ArrayList<>();final Queue<CompletableFuture<HttpResponse<String>>> pending=new ArrayDeque<>();
  public Optional<java.net.CookieHandler> cookieHandler(){return Optional.empty();}public Optional<Duration> connectTimeout(){return Optional.of(Duration.ofSeconds(1));}
  public Redirect followRedirects(){return Redirect.NEVER;}public Optional<ProxySelector> proxy(){return Optional.empty();}
  public SSLContext sslContext(){try{return SSLContext.getDefault();}catch(Exception e){throw new AssertionError(e);}}public SSLParameters sslParameters(){return new SSLParameters();}
  public Optional<java.net.Authenticator> authenticator(){return Optional.empty();}public Version version(){return Version.HTTP_1_1;}public Optional<Executor> executor(){return Optional.empty();}
  public <T> HttpResponse<T> send(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
  @SuppressWarnings("unchecked") public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){
   calls.add(r);if(r.uri().getPath().endsWith("/disconnect") || r.uri().getPath().endsWith("/revoke"))return CompletableFuture.completedFuture(new Response<>(r,200,null));
   var future=new CompletableFuture<HttpResponse<String>>();pending.add(future);return (CompletableFuture<HttpResponse<T>>)(Object)future;
  }
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return sendAsync(r,h);}public void shutdownNow(){}
 }
 static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
 static Object session(ServerPlayer p)throws Exception{Field f=TerminalPassportServer.class.getDeclaredField("sessions");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(p.connection);}
 static class Fixture implements AutoCloseable {
  final MinecraftServer server=new MinecraftServer();final ServerPlayer player=player(server);final Transport http=new Transport();
  String id=UUID.randomUUID().toString();
  Fixture()throws Exception{TerminalPassportServer.close();Field f=TerminalPassportServer.class.getDeclaredField("config");f.setAccessible(true);f.set(null,new CoreConfig.TerminalSso(true,ENDPOINT,KEY));f=TerminalPassportServer.class.getDeclaredField("http");f.setAccessible(true);f.set(null,http);PacketDistributor.deliveries.clear();TerminalPassportServer.admit(player);}
  String gameSession()throws Exception{return (String)field(session(player),"id");}
  JsonObject result()throws Exception{var b=new JsonObject();b.addProperty("uid",10000);b.addProperty("gameSession",gameSession());b.addProperty("requestId",id);b.addProperty("expiresInSeconds",30);return b;}
  void create(){TerminalPassportServer.request(player,new TerminalPassportNetwork.Request(id,"",0));}
  void claim()throws Exception{TerminalPassportServer.request(player,new TerminalPassportNetwork.Request(id,gameSession(),1));}
  void reply(int status,String body){http.pending.remove().complete(new Response<>(http.calls.getLast(),status,body));}
  void flush(){server.flush();}
  void establish()throws Exception{create();reply(200,result().toString());flush();check(!social(player),"public challenge never grants social trust");claim();reply(200,result().toString());check(!social(player),"claim must return to server thread");flush();check(social(player),"Auth accepted existing access establishes social trust");}
  void checkStatus()throws Exception{set(session(player),"nextCheck",0L);TerminalPassportServer.tick(new ServerTickEvent.Post());}
  public void close(){TerminalPassportServer.close();}
 }
 public static void main(String[] args)throws Exception{
  try(var f=new Fixture()){
   check(!social(f.player) && !join(f.player),"numeric offline profile is not an authenticated account");f.establish();
   check(!join(f.player),"terminal access cannot grant settlement admission");
   check(f.http.calls.getFirst().uri().toString().equals(ENDPOINT+"terminal-context/create"),"dedicated context endpoint");
   check(f.http.calls.stream().allMatch(r->r.headers().firstValue("X-Muxi-Server-Key").orElse("").equals(KEY) && r.headers().firstValue("Authorization").isEmpty()),"only machine key reaches server transport");
   var result=PacketDistributor.deliveries.getLast().packet();check(result.status()==2 && result.uid()==10000 && result.gameSession().equals(f.gameSession()),"game packet contains correlation and UID only");
   f.checkStatus();var status=f.result();status.addProperty("authenticated",false);f.reply(200,status.toString());f.flush();check(!social(f.player),"source expiry revokes social trust");
  }
  try(var f=new Fixture()){
   f.establish();TrustedAccounts.admit(f.player);f.checkStatus();var status=f.result();status.addProperty("authenticated",false);f.reply(200,status.toString());f.flush();
   check(join(f.player),"native account expiry preserves independent verified game admission");TrustedAccounts.revoke(f.player);
  }
  for(String key:List.of("uid","requestId","gameSession","expiresInSeconds"))for(boolean missing:List.of(false,true))try(var f=new Fixture()){
   f.create();var b=f.result();if(missing)b.remove(key);else b.addProperty(key,"invalid");f.reply(200,b.toString());f.flush();check(!social(f.player),"malformed context denied "+key);check(PacketDistributor.deliveries.getLast().packet().status()==0,"malformed context yields denial");
  }
  for(int code:List.of(401,403,503,302))try(var f=new Fixture()){
   f.create();f.reply(code,f.result().toString());f.flush();check(!social(f.player) && !join(f.player),"issuer status fails closed "+code);
  }
  try(var f=new Fixture()){
   f.create();var response=f.result().toString();TerminalPassportServer.disconnect(f.player);var renewed=player(f.server);TerminalPassportServer.admit(renewed);
   f.reply(200,response);f.flush();check(!social(renewed) && PacketDistributor.deliveries.isEmpty(),"late old listener response cannot authenticate reconnect");
   TrustedAccounts.admitTerminal(renewed,10000);TerminalPassportServer.disconnect(f.player);check(social(renewed),"old logout cannot clear current listener trust");TerminalPassportServer.disconnect(renewed);check(!social(renewed),"current logout clears trust");
  }
  try(var f=new Fixture()){
   f.establish();f.checkStatus();var stale=f.http.pending.remove();set(session(f.player),"last",0L);f.id=UUID.randomUUID().toString();f.create();f.reply(200,f.result().toString());f.flush();f.claim();f.reply(200,f.result().toString());f.flush();
   var body=f.result();body.addProperty("authenticated",false);stale.complete(new Response<>(f.http.calls.getFirst(),200,body.toString()));f.flush();check(social(f.player),"late old heartbeat cannot revoke a newly accepted binding");
   TerminalPassportServer.request(f.player,new TerminalPassportNetwork.Request(UUID.randomUUID().toString(),f.gameSession(),2));check(!social(f.player),"explicit native revocation clears social trust");
  }
  try(var f=new Fixture()){f.create();f.player.connection.online=false;f.reply(200,f.result().toString());f.flush();check(!social(f.player) && PacketDistributor.deliveries.isEmpty(),"offline listener cannot receive context");}
  try(var f=new Fixture()){f.player.profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"10000");f.create();check(f.http.calls.isEmpty(),"UID/offline UUID mismatch never contacts Auth");}
  try(var f=new Fixture()){TerminalPassportServer.request(f.player,new TerminalPassportNetwork.Request("-".repeat(36),"",0));check(f.http.calls.isEmpty(),"invalid correlation UUID rejected");}
  check(!CoreConfig.parse("{}").terminalSso().enabled(),"SSO disabled by default");
  var config=CoreConfig.parse("{\"features\":{\"terminalSso\":{\"enabled\":true,\"serverKey\":\""+KEY+"\"}}}");check(!config.login().enabled() && !config.opSync().enabled(),"SSO never activates LoginGate or OP");check(!config.toString().contains(KEY),"machine key is redacted");
  System.out.println("Original-access server lifecycle assertions: "+assertions+" passed; deterministic listener fixtures, no production requests");
 }
}
