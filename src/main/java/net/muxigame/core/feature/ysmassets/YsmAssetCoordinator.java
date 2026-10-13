package net.muxigame.core.feature.ysmassets;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Asset protocol core. Existing Auth hooks own its lifecycle; runtime/import remains disabled by default. */
public final class YsmAssetCoordinator implements AutoCloseable {
    public record Identity(String uid,UUID playerId,Object listener) {
        public Identity {YsmAssetSnapshot.uid(uid);Objects.requireNonNull(playerId);Objects.requireNonNull(listener);}
    }
    @FunctionalInterface public interface Connection {
        Identity current();
        /** Fallback may revoke wear after auth expires, but never target a replacement listener. */
        default boolean fallbackTargetCurrent(Identity expected){
            try{var now=current();return now.listener()==expected.listener()&&now.playerId().equals(expected.playerId());}catch(RuntimeException absent){return false;}
        }
    }
    /** Native adapter must prove all incoming native use/download paths, not just this bridge. */
    public interface RuntimePort {
        default boolean exactGenerationReady(long revision,String generation){return false;}
        default boolean wear(Identity player,String logicalHandle,String contentId,long revision,String generation){return false;}
        default Fallback fallbackAndInvalidate(Identity player,long revision,String generation){return new Fallback(false,false,false);}
    }
    public record Fallback(boolean defaultConfirmed,boolean ownCacheInvalidated,boolean trackingPeersAcknowledged) {
        public boolean complete(){return defaultConfirmed&&ownCacheInvalidated&&trackingPeersAcknowledged;}
    }
    /** Internal permit, never serialized as a client capability or trusted on its own. */
    public record Permit(Identity identity,long epoch,long revision,String generation,String logicalHandle,String contentId,String sha256) {}
    public record Projection(String modelId,boolean ownReference,boolean canSelect) {}
    public record StageReceipt(long revision,String generation,int pendingFallbacks,boolean active) {}
    private record Selected(Identity identity,String handle,String contentId,String sha256,Connection connection) {}
    private record Replay(String handle,Permit permit) {}
    private final RuntimePort runtime;private final BooleanSupplier serverThread;
    private YsmAssetSnapshot snapshot=YsmAssetSnapshot.empty();private String generation="";private long epoch;
    private final Map<Object,Selected> selected=new IdentityHashMap<>();private final Map<Object,Map<String,Replay>> requests=new IdentityHashMap<>();private final Map<Object,Selected> pending=new IdentityHashMap<>();
    private boolean closed;
    public YsmAssetCoordinator(BooleanSupplier serverThread){this(serverThread,new RuntimePort(){});}
    /** A runtime implementation is trusted server code, not a receipt deserialized from Web/client JSON. */
    public YsmAssetCoordinator(BooleanSupplier serverThread,RuntimePort runtime){this.serverThread=Objects.requireNonNull(serverThread);this.runtime=Objects.requireNonNull(runtime);}
    private void thread(){if(closed||!serverThread.getAsBoolean())throw new IllegalStateException("YSM bridge inactive or wrong thread");}
    private static IllegalArgumentException denied(){return new IllegalArgumentException("YSM asset unavailable");}
    private Identity identity(Connection connection){thread();try{return Objects.requireNonNull(connection.current());}catch(RuntimeException e){throw denied();}}
    private boolean ready(){return snapshot.revision()>=0 && pending.isEmpty() && runtime.exactGenerationReady(snapshot.revision(),generation);}
    private YsmAssetSnapshot.Content permitted(Identity who,String handle,long expected){
        if(snapshot.revision()!=expected)throw denied();var content=snapshot.permitted(who.uid(),handle);if(content==null)throw denied();return content;
    }
    public List<Projection> list(Connection connection){
        Identity who=identity(connection);boolean ready=ready();return snapshot.visibleHandles(who.uid()).stream().map(id->new Projection(id,id.startsWith("m_"),ready)).toList();
    }
    public Projection metadata(Connection connection,String handle,long expected){Identity who=identity(connection);permitted(who,handle,expected);return new Projection(handle,handle.startsWith("m_"),ready());}
    public Permit downloadPermit(Connection connection,String handle,long expected){
        Identity who=identity(connection);var c=permitted(who,handle,expected);if(!ready())throw denied();return new Permit(who,epoch,snapshot.revision(),generation,handle,c.contentId(),c.sha256());
    }
    /** Call again immediately before open/send and before every chunk/native delivery. */
    public void validatePermit(Connection connection,Permit permit){
        Identity who=identity(connection);
        if(permit==null||who.listener()!=permit.identity().listener()||!who.uid().equals(permit.identity().uid())||!who.playerId().equals(permit.identity().playerId())||permit.epoch()!=epoch||!generation.equals(permit.generation())||!ready())throw denied();
        var c=permitted(who,permit.logicalHandle(),permit.revision());if(!c.contentId().equals(permit.contentId())||!c.sha256().equals(permit.sha256()))throw denied();
    }
    public Permit select(Connection connection,String handle,long expected,String requestKey){
        if(requestKey==null||!requestKey.matches("[A-Za-z0-9._:-]{8,128}"))throw denied();
        Permit permit=downloadPermit(connection,handle,expected);var replay=requests.computeIfAbsent(permit.identity().listener(),k->new HashMap<>());var prior=replay.get(requestKey);
        if(prior!=null){if(!prior.handle().equals(handle))throw denied();validatePermit(connection,prior.permit());return prior.permit();}
        if(replay.size()>=256)throw denied();validatePermit(connection,permit);
        var tentative=new Selected(permit.identity(),handle,permit.contentId(),permit.sha256(),connection);selected.put(permit.identity().listener(),tentative);
        try{
            if(!runtime.wear(permit.identity(),handle,permit.contentId(),permit.revision(),permit.generation()))throw denied();
            validatePermit(connection,permit);replay.put(requestKey,new Replay(handle,permit));return permit;
        }catch(RuntimeException failed){
            selected.remove(permit.identity().listener());pending.put(permit.identity().listener(),tentative);reconcile();throw denied();
        }
    }
    /** Apply only this live actor's desired selection from the trusted Web snapshot. */
    public Optional<Permit> applyDesiredSelection(Connection connection){
        Identity who=identity(connection);String handle=snapshot.selections().get(who.uid());
        if(handle==null)return Optional.empty();return Optional.of(select(connection,handle,snapshot.revision(),"web-desired:"+generation+":"+who.uid()));
    }
    /** Publish permission fence before fallback work. Failed fallback never restores old ACL. */
    public StageReceipt stage(YsmAssetSnapshot next,String nextGeneration){
        thread();Objects.requireNonNull(next);if(next.revision()<0||next.revision()<snapshot.revision()||nextGeneration==null||!nextGeneration.matches("g_[a-f0-9]{32}"))throw denied();
        if(next.revision()==snapshot.revision()){
            if(!next.equals(snapshot)||!generation.equals(nextGeneration))throw denied();return new StageReceipt(snapshot.revision(),generation,pending.size(),false);
        }
        snapshot=next;generation=nextGeneration;epoch++;
        for(var entry:new ArrayList<>(selected.entrySet())){
            var old=entry.getValue();var c=snapshot.permitted(old.identity().uid(),old.handle());
            if(c==null||!c.contentId().equals(old.contentId())||!c.sha256().equals(old.sha256())){selected.remove(entry.getKey());pending.put(entry.getKey(),old);}
        }
        // Old idempotency keys stay fenced by their old epoch; a new key is required.
        reconcile();return new StageReceipt(snapshot.revision(),generation,pending.size(),false);
    }
    public StageReceipt reconcile(){
        thread();for(var entry:new ArrayList<>(selected.entrySet())){
            var value=entry.getValue();try{
                var current=identity(value.connection());if(current.listener()!=value.identity().listener()||!current.uid().equals(value.identity().uid()))throw denied();
            }catch(RuntimeException expired){selected.remove(entry.getKey());pending.put(entry.getKey(),value);}
        }
        for(var entry:new ArrayList<>(pending.entrySet())){
            var value=entry.getValue();
            if(!value.connection().fallbackTargetCurrent(value.identity())){pending.remove(entry.getKey());requests.remove(entry.getKey());continue;}
            try{if(runtime.fallbackAndInvalidate(value.identity(),snapshot.revision(),generation).complete())pending.remove(entry.getKey());}catch(RuntimeException retryLater){}
        }
        return new StageReceipt(snapshot.revision(),generation,pending.size(),false);
    }
    /** Disconnect only that exact listener; old logout cannot clear the replacement connection. */
    public void disconnect(Identity identity){thread();selected.remove(identity.listener());requests.remove(identity.listener());pending.remove(identity.listener());}
    /** Remote private appearance is NOT a byte-download grant. No render adapter is provided. */
    public boolean canReceivePrivateRenderBytes(Connection observer,String targetHandle,long expected){
        Identity who=identity(observer);return expected==snapshot.revision()&&snapshot.permitted(who.uid(),targetHandle)!=null&&ready();
    }
    public long revision(){thread();return snapshot.revision();}
    @Override public void close(){
        thread();epoch++;snapshot=YsmAssetSnapshot.empty();generation="";pending.putAll(selected);selected.clear();requests.clear();reconcile();
        int missing=pending.size();pending.clear();closed=true;if(missing!=0)throw new IllegalStateException("YSM bridge stopped with unconfirmed wear fallback");
    }
}
