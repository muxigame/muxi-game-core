package net.muxigame.core.feature.ysmassets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Machine wire binding metadata, not credentials. Issuer hooks must bind/revoke each authorization generation. */
public final class YsmConnectionRegistry implements AutoCloseable {
    public record Claim(String serverId,String connectionId,String bindingGeneration,String uid) {}
    private record Binding(ServerPlayer player,YsmAssetCoordinator.Connection connection,Claim claim,Object listener) {}
    private final MinecraftServer server;private final String serverId;
    private final Map<String,Binding> bindings=new HashMap<>();private final Map<Object,String> byListener=new IdentityHashMap<>();private boolean closed;
    public YsmConnectionRegistry(MinecraftServer server,String serverId){
        this.server=Objects.requireNonNull(server);if(serverId==null||!serverId.matches("[A-Za-z0-9._:-]{1,128}"))throw new IllegalArgumentException("Invalid YSM game server id");this.serverId=serverId;
    }
    private void thread(){if(closed||!server.isSameThread())throw new IllegalStateException("YSM bindings unavailable");}
    private static IllegalArgumentException denied(){return new IllegalArgumentException("YSM game connection unavailable");}
    /** Trusted admission callback only. Rebinding the same UID/listener always retires prior claims. */
    public Claim bindVerified(ServerPlayer player){
        thread();if(player.server!=server)throw denied();var connection=YsmTrustedPlayer.capture(player);var who=connection.current();String old=byListener.remove(who.listener());if(old!=null)bindings.remove(old);
        var claim=new Claim(serverId,UUID.randomUUID().toString(),UUID.randomUUID().toString(),who.uid());var binding=new Binding(player,connection,claim,who.listener());bindings.put(claim.connectionId(),binding);byListener.put(who.listener(),claim.connectionId());return claim;
    }
    /** Client entry points derive their claim from the actual packet sender, never body.uid. */
    public Claim claimFor(ServerPlayer player){
        thread();if(player.server!=server)throw denied();var who=YsmTrustedPlayer.capture(player).current();var key=byListener.get(who.listener());var binding=key==null?null:bindings.get(key);
        if(binding==null||binding.player()!=player||!binding.claim().uid().equals(who.uid()))throw denied();resolve(binding.claim()).current();return binding.claim();
    }
    public YsmAssetCoordinator.Connection connectionFor(ServerPlayer player){return resolve(claimFor(player));}
    public YsmAssetCoordinator.Connection resolve(Claim claim){
        thread();if(claim==null||!serverId.equals(claim.serverId()))throw denied();var binding=bindings.get(claim.connectionId());if(binding==null||!binding.claim().equals(claim))throw denied();
        try{var who=binding.connection().current();if(who.listener()!=binding.listener()||!who.uid().equals(claim.uid()))throw denied();}catch(RuntimeException stale){retire(binding.claim());throw denied();}
        // Recheck registry generation AND actual trust every time a previously returned handle is used.
        return new YsmAssetCoordinator.Connection(){
            @Override public YsmAssetCoordinator.Identity current(){
                thread();if(bindings.get(claim.connectionId())!=binding)throw denied();return binding.connection().current();
            }
            @Override public boolean fallbackTargetCurrent(YsmAssetCoordinator.Identity expected){
                thread();String newer=byListener.get(expected.listener());
                return (newer==null||newer.equals(claim.connectionId()))&&binding.connection().fallbackTargetCurrent(expected);
            }
        };
    }
    /** Exact-generation logout/expiry. A late old callback cannot retire a new claim. */
    public void retire(Claim claim){
        thread();if(claim==null||!serverId.equals(claim.serverId()))return;var binding=bindings.get(claim.connectionId());if(binding==null||!binding.claim().equals(claim))return;
        bindings.remove(claim.connectionId());byListener.remove(binding.listener(),claim.connectionId());
    }
    public void prune(){
        thread();for(var b:new ArrayList<>(bindings.values()))try{b.connection().current();}catch(RuntimeException stale){retire(b.claim());}
    }
    @Override public void close(){thread();bindings.clear();byListener.clear();closed=true;}
}
