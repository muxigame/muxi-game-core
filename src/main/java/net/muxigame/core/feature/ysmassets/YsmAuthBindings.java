package net.muxigame.core.feature.ysmassets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Passive identity lifecycle, called by existing verified Auth hooks. Model runtime stays closed by default. */
public final class YsmAuthBindings {
    public enum Source { JOIN, TERMINAL }
    private static final EnumSet<Source> configuredSources=EnumSet.noneOf(Source.class);
    private static final Map<MinecraftServer,Scope> scopes=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final class Admission {
        final ServerPlayer player;final YsmAssetCoordinator.Identity identity;
        final EnumSet<Source> sources=EnumSet.noneOf(Source.class);YsmConnectionRegistry.Claim claim;
        Admission(ServerPlayer player){this.player=player;identity=YsmTrustedPlayer.capture(player).current();}
    }
    private static final class Scope {
        final MinecraftServer server;final YsmConnectionRegistry registry;final YsmAssetCoordinator coordinator;
        final Map<Object,Admission> actors=new IdentityHashMap<>();long lastTick;
        Scope(MinecraftServer server,String id,YsmAssetCoordinator.RuntimePort port){this.server=server;registry=new YsmConnectionRegistry(server,id);coordinator=new YsmAssetCoordinator(server::isSameThread,port);}
    }
    private YsmAuthBindings() {}
    private static void thread(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("YSM identity lifecycle requires server thread");}
    private static Scope scope(MinecraftServer server){
        thread(server);return scopes.computeIfAbsent(server,s->new Scope(s,"unregistered-"+UUID.randomUUID(),new YsmAssetCoordinator.RuntimePort(){}));
    }
    /** Approved startup injection only, before any admission; not exposed through a client or HTTP request. */
    public static void attach(MinecraftServer server,String approvedServerId,YsmAssetCoordinator.RuntimePort runtime){
        thread(server);var old=scopes.get(server);if(old!=null&&!old.actors.isEmpty())throw new IllegalStateException("Configure YSM machine identity before admitting players");
        if(old!=null){old.coordinator.close();old.registry.close();}scopes.put(server,new Scope(server,approvedServerId,Objects.requireNonNull(runtime)));
    }
    public static void sourceStarted(Source source){synchronized(scopes){configuredSources.add(source);}}
    public static void joinVerified(ServerPlayer player){net.muxigame.minigames.TrustedAccounts.uid(player);verified(player,Source.JOIN);}
    public static void terminalVerified(ServerPlayer player){verified(player,Source.TERMINAL);}
    private static void verified(ServerPlayer player,Source source){
        var s=scope(player.server);var who=YsmTrustedPlayer.capture(player).current();var a=s.actors.get(who.listener());
        if(a!=null&&a.player!=player)throw new IllegalStateException("YSM listener already belongs to another player");
        if(a==null){a=new Admission(player);s.actors.put(who.listener(),a);}a.sources.add(source);sourceStarted(source);rotate(s,a);
    }
    private static void rotate(Scope s,Admission a){
        if(a.claim!=null){s.registry.retire(a.claim);a.claim=null;}
        // Retiring claims fences in-flight reads before any wear/cache acknowledgement.
        if(s.coordinator.reconcile().pendingFallbacks()!=0)return;
        if(!a.sources.isEmpty())try{a.claim=s.registry.bindVerified(a.player);}catch(IllegalArgumentException noCurrentTrust){}
    }
    public static void terminalRevoked(ServerPlayer player,Object originalListener){revoked(player,originalListener,Source.TERMINAL);}
    private static void revoked(ServerPlayer player,Object listener,Source source){
        thread(player.server);var s=scopes.get(player.server);if(s==null)return;var a=s.actors.get(listener);if(a==null||a.player!=player)return;a.sources.remove(source);rotate(s,a);
        if(a.sources.isEmpty()&&s.coordinator.reconcile().pendingFallbacks()==0)s.actors.remove(listener);
    }
    public static void disconnected(ServerPlayer player,Object originalListener){
        thread(player.server);var s=scopes.get(player.server);if(s==null)return;var a=s.actors.get(originalListener);if(a==null||a.player!=player)return;
        if(a.claim!=null)s.registry.retire(a.claim);s.coordinator.disconnect(a.identity);s.actors.remove(originalListener);
    }
    public static YsmConnectionRegistry.Claim claimFor(ServerPlayer player){
        thread(player.server);var s=scopes.get(player.server);var a=s==null?null:s.actors.get(player.connection);
        if(a==null||a.player!=player||a.claim==null||a.sources.isEmpty())throw new IllegalArgumentException("YSM verified binding unavailable");return s.registry.claimFor(player);
    }
    /** Approved machine resolver must return this actual value; caller claims are never authority. */
    public static YsmAssetCoordinator.Identity resolve(MinecraftServer server,YsmConnectionRegistry.Claim claim){
        thread(server);var s=scopes.get(server);if(s==null)throw new IllegalArgumentException("YSM verified binding unavailable");return s.registry.resolve(claim).current();
    }
    public static YsmAssetCoordinator.Connection connectionFor(ServerPlayer player){claimFor(player);return scopes.get(player.server).registry.connectionFor(player);}
    public static YsmAssetCoordinator coordinator(MinecraftServer server){thread(server);return scope(server).coordinator;}
    public static void tick(ServerTickEvent.Post ignored){
        List<Scope> values; synchronized(scopes){values=new ArrayList<>(scopes.values());}
        long now=System.nanoTime();for(var s:values)if(s.server.isSameThread()&&now-s.lastTick>=40_000_000L){s.lastTick=now;reconcile(s.server);}
    }
    public static void reconcile(MinecraftServer server){
        thread(server);var s=scopes.get(server);if(s==null)return;s.registry.prune();s.coordinator.reconcile();
        for(var entry:new ArrayList<>(s.actors.entrySet())){
            var a=entry.getValue();
            if(!YsmTrustedPlayer.captureTransportCurrent(a.player,a.identity)){
                if(a.claim!=null)s.registry.retire(a.claim);s.coordinator.disconnect(a.identity);s.actors.remove(entry.getKey());continue;
            }
            try{YsmTrustedPlayer.capture(a.player).current();}catch(IllegalArgumentException noTrust){a.sources.clear();if(a.claim!=null)s.registry.retire(a.claim);a.claim=null;}
            if(a.claim!=null)try{s.registry.resolve(a.claim).current();}catch(IllegalArgumentException stale){a.claim=null;}
            if(a.claim==null&&s.coordinator.reconcile().pendingFallbacks()==0){if(a.sources.isEmpty())s.actors.remove(entry.getKey());else rotate(s,a);}
        }
    }
    /** Feature shutdown retires only its own trust source; independent verified admission remains independent. */
    public static void sourceStopped(Source source){
        List<Scope> values; synchronized(scopes){configuredSources.remove(source);values=new ArrayList<>(scopes.values());}
        for(var s:values){Runnable work=()->{
            for(var a:new ArrayList<>(s.actors.values()))if(a.sources.remove(source))rotate(s,a);
            boolean noFeatures; synchronized(scopes){noFeatures=configuredSources.isEmpty();}
            if(noFeatures){
                try{s.coordinator.close();}catch(RuntimeException unconfirmed){
                    System.getLogger("MuxiGameCore/YsmIdentity").log(System.Logger.Level.WARNING,"YSM shutdown wear fallback unconfirmed; model runtime remains inadmissible");
                }finally{s.registry.close();s.actors.clear();scopes.remove(s.server,s);}
            }
        };if(s.server.isSameThread())work.run();else s.server.execute(work);}
    }
}
