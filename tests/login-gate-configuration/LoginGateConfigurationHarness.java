package net.muxigame.core.feature.login;

import com.mojang.authlib.GameProfile;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.minigames.TrustedAccounts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Actual source + HTTP, fixture Minecraft lifecycle; never an actual player acceptance claim. */
public final class LoginGateConfigurationHarness {
    static int checks;
    static final String KEY="isolated-login-gate-fixture-key";
    static void check(String name,boolean condition){if(!condition)throw new AssertionError(name);checks++;}
    static boolean untrusted(ServerPlayer p){try{TrustedAccounts.uid(p);return false;}catch(IllegalArgumentException absent){return true;}}
    static String identity(String uid){return "{\"uid\":\""+uid+"\",\"loginName\":\""+uid+"\",\"offlineUuid\":\""+IdentityRules.offlineUuid(uid)+"\"}";}
    static final class Bus implements IEventBus {
        final List<Consumer<Object>> listeners=new ArrayList<>();
        @SuppressWarnings("unchecked") public <T>void addListener(Consumer<T> listener){listeners.add((Consumer<Object>)(Object)listener);}
        void fire(int index,Object event){listeners.get(index).accept(event);}
    }
    static final class Reply {
        final int code;final String body;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);
        Reply(int code,String body,boolean blocked){this.code=code;this.body=body;if(!blocked)release.countDown();}
    }
    static final class Fixture implements AutoCloseable {
        final MinecraftServer server=new MinecraftServer();final Bus modBus=new Bus(),gameBus=new Bus();
        final HttpServer auth;final ExecutorService workers=Executors.newCachedThreadPool();
        final Queue<Reply> replies=new ConcurrentLinkedQueue<>();final AtomicInteger requests=new AtomicInteger();
        final List<String> paths=new CopyOnWriteArrayList<>();final LoginGate gate;final List<String> errors=new CopyOnWriteArrayList<>();
        Fixture()throws Exception {
            auth=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            auth.setExecutor(workers);auth.createContext("/join/",exchange->{
                requests.incrementAndGet();paths.add(exchange.getRequestURI().getPath());
                if(!"POST".equals(exchange.getRequestMethod())||!KEY.equals(exchange.getRequestHeaders().getFirst("X-Muxi-Server-Key")))errors.add("Wrong request contract");
                Reply reply=replies.remove();reply.entered.countDown();
                try {if(!reply.release.await(8,TimeUnit.SECONDS))throw new AssertionError("Reply release timeout");
                    byte[] bytes=reply.body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(reply.code,bytes.length);exchange.getResponseBody().write(bytes);
                }catch(Exception ignored){}finally{exchange.close();reply.done.countDown();}
            });auth.start();
            gate=new LoginGate(new CoreConfig.Login(true,"http://127.0.0.1:"+auth.getAddress().getPort()+"/join/",KEY,1));
            gate.registerConfigurationTasks(modBus);gate.register(gameBus);
        }
        Reply plan(int code,String body,boolean blocked){Reply r=new Reply(code,body,blocked);replies.add(r);return r;}
        ServerConfigurationPacketListenerImpl listener(String uid,UUID id){return new ServerConfigurationPacketListenerImpl(server,new Connection(),new GameProfile(id,uid));}
        ConfigurationTask task(ServerConfigurationPacketListenerImpl listener){RegisterConfigurationTasksEvent event=new RegisterConfigurationTasksEvent(listener);modBus.fire(0,event);
            check("one configuration task",event.tasks.size()==1);return event.tasks.getFirst();}
        ServerPlayer player(String uid,Connection raw){ServerPlayer p=new ServerPlayer(server,uid,IdentityRules.offlineUuid(uid));p.connection.raw=raw;raw.listener=p.connection;return p;}
        void login(ServerPlayer player){gameBus.fire(0,new PlayerEvent.PlayerLoggedInEvent(player));}
        void logout(ServerPlayer player){gameBus.fire(1,new PlayerEvent.PlayerLoggedOutEvent(player));}
        void await(ServerConfigurationPacketListenerImpl listener)throws Exception {server.until(()->listener.finished>0||!listener.raw.isConnected());}
        void processed(Reply reply)throws Exception {check("HTTP response sent",reply.done.await(3,TimeUnit.SECONDS));server.until(()->server.enqueued.get()>0);server.flush();}
        public void close(){gate.close();for(Reply reply:replies)reply.release.countDown();auth.stop(0);workers.shutdownNow();check("HTTP request contract",errors.isEmpty());}
    }
    static void successfulTwoConnections()throws Exception {
        try(Fixture f=new Fixture()){
            check("mod bus registration",f.modBus.listeners.size()==1);check("game bus only player lifecycle",f.gameBus.listeners.size()==2);
            for(String uid:List.of("10000","10001")){
                ServerConfigurationPacketListenerImpl listener=f.listener(uid,IdentityRules.offlineUuid(uid));
                Reply r=f.plan(200,identity(uid),true);ConfigurationTask task=f.task(listener);
                check("registered task has not consumed grant",f.requests.get()==Integer.parseInt(uid)-10000);
                task.start(packet->{});check("request reached Auth",r.entered.await(3,TimeUnit.SECONDS));
                check("waits for verified response",listener.finished==0);task.start(packet->{});
                check("task starts once",f.requests.get()==Integer.parseInt(uid)-9999);
                r.release.countDown();f.await(listener);check("advances exactly once",listener.finished==1);
                ServerPlayer p=f.player(uid,listener.raw);f.login(p);
                check("same connection admitted to normal account",Long.parseLong(uid)==TrustedAccounts.uid(p));
                f.logout(p);check("logout revokes normal account",untrusted(p));
                f.login(p);check("late admission cannot replay",!listener.raw.isConnected());
            }
            check("both UID routes used",f.paths.equals(List.of("/join/10000","/join/10001")));
        }
    }
    static void reject(String label,int code,String body)throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));f.plan(code,body,false);f.task(listener).start(packet->{});f.await(listener);
            check(label+" disconnects",!listener.raw.isConnected());check(label+" does not finish task",listener.finished==0);
            ServerPlayer p=f.player("10000",listener.raw);f.login(p);check(label+" has no trust",untrusted(p));
        }
    }
    static void invalidProfile(String uid,UUID id)throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener(uid,id);f.task(listener).start(packet->{});
            check("invalid profile refused",!listener.raw.isConnected());check("invalid profile never looks up grant",f.requests.get()==0);
            check("invalid profile never finishes",listener.finished==0);
        }
    }
    static void exactConnectionAndLateFallback()throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));f.plan(200,identity("10000"),false);f.task(listener).start(packet->{});f.await(listener);
            ServerPlayer other=f.player("10000",new Connection());f.login(other);
            check("same UID cannot borrow raw connection admission",!other.connection.raw.isConnected()&&untrusted(other));
            ServerPlayer exact=f.player("10000",listener.raw);f.login(exact);check("correct connection retains its admission",10000L==TrustedAccounts.uid(exact));
            ServerPlayer social=f.player("10001",new Connection());TrustedAccounts.admitTerminal(social,10001L);
            check("fixture has social trust only",10001L==TrustedAccounts.socialUid(social)&&untrusted(social));
            f.login(social);check("social trust cannot bypass LoginGate",!social.connection.raw.isConnected()&&untrusted(social));
        }
    }
    static void staleCallback(boolean disconnect)throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));Reply r=f.plan(200,identity("10000"),true);f.task(listener).start(packet->{});
            check("pending request entered",r.entered.await(3,TimeUnit.SECONDS));
            if(disconnect)listener.raw.connected=false;else listener.raw.listener=new Object();
            r.release.countDown();f.processed(r);check("stale callback never advances",listener.finished==0);
            listener.raw.connected=true;ServerPlayer p=f.player("10000",listener.raw);f.login(p);
            check("stale callback never records evidence",!listener.raw.isConnected()&&untrusted(p));
        }
    }
    static void failedTaskAdvance()throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));listener.failFinish=true;
            f.plan(200,identity("10000"),false);f.task(listener).start(packet->{});f.await(listener);
            check("task advance failure refuses",!listener.raw.isConnected()&&listener.finished==0);
            listener.raw.connected=true;ServerPlayer p=f.player("10000",listener.raw);f.login(p);
            check("failed task advance discards evidence",!listener.raw.isConnected()&&untrusted(p));
        }
    }
    static void identityChangedBeforeLogin()throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));f.plan(200,identity("10000"),false);f.task(listener).start(packet->{});f.await(listener);
            ServerPlayer changed=f.player("10001",listener.raw);f.login(changed);
            check("same raw connection cannot change verified UID and UUID",!listener.raw.isConnected()&&untrusted(changed));
        }
    }
    static void shutdown()throws Exception {
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));Reply r=f.plan(200,identity("10000"),true);f.task(listener).start(packet->{});
            check("shutdown request entered",r.entered.await(3,TimeUnit.SECONDS));f.gate.close();r.release.countDown();f.await(listener);
            check("shutdown never advances",listener.finished==0);ServerPlayer p=f.player("10000",listener.raw);f.login(p);
            check("shutdown cannot admit",untrusted(p));
            var next=f.listener("10001",IdentityRules.offlineUuid("10001"));f.task(next).start(packet->{});
            check("stopped feature refuses new task",!next.raw.isConnected()&&next.finished==0);
        }
    }
    public static void main(String[] args)throws Exception {
        successfulTwoConnections();
        reject("absent or expired one-use grant",409,"{}");reject("unknown UID",404,"{}");reject("wrong service key",403,"{}");
        reject("Auth unavailable",503,"{}");reject("redirect",302,identity("10000"));reject("malformed identity",200,"{}");
        reject("wrong loginName",200,identity("10000").replace("\"loginName\":\"10000\"","\"loginName\":\"10001\""));
        reject("wrong UID",200,identity("10000").replace("\"uid\":\"10000\"","\"uid\":\"10001\""));
        reject("wrong UUID",200,identity("10000").replace(IdentityRules.offlineUuid("10000").toString(),IdentityRules.offlineUuid("10001").toString()));
        invalidProfile("nickname",UUID.randomUUID());invalidProfile("10000",IdentityRules.offlineUuid("10001"));invalidProfile("10000",null);
        exactConnectionAndLateFallback();identityChangedBeforeLogin();staleCallback(false);staleCallback(true);failedTaskAdvance();shutdown();
        try(Fixture f=new Fixture()){
            var listener=f.listener("10000",IdentityRules.offlineUuid("10000"));Reply r=f.plan(200,identity("10000"),true);f.task(listener).start(packet->{});f.await(listener);
            check("HTTP timeout refuses without advancing",!listener.raw.isConnected()&&listener.finished==0);r.release.countDown();
        }
        System.out.println("LoginGate configuration HTTP/lifecycle fixture: "+checks+" assertions passed; no MC launched");
    }
}
