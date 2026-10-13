package net.muxigame.core.feature.ysmassets;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import java.util.*;

/** Explicit server default observation only. Never grants readiness, wear, cache or peer ACK. */
public final class YsmProduction265Fallback implements YsmAssetCoordinator.RuntimePort {
    public record Observation(boolean serverDefaultObserved,boolean resetAttempted,String status) {
        public Observation {if(status==null||!status.matches("[A-Z_]{1,64}"))throw new IllegalArgumentException("Invalid default reset observation");}
    }
    @FunctionalInterface public interface Resetter {
        Observation reset(MinecraftServer server,ServerPlayer player,UUID playerId,ServerGamePacketListenerImpl listener)throws Exception;
    }
    private final MinecraftServer server;private final Resetter resetter;
    private Observation last=new Observation(false,false,"UNCONFIGURED");
    /** Trusted startup callback only; not deserialized from client, Web or an observation JSON. */
    public YsmProduction265Fallback(MinecraftServer server,Resetter resetter){this.server=Objects.requireNonNull(server);this.resetter=Objects.requireNonNull(resetter);}
    /** Inject the original helper already bound to the approved pinned JAR; no implicit bind or native load. */
    public static YsmProduction265Fallback fromApprovedHelper(MinecraftServer server,Object helper){
        if(helper==null||!helper.getClass().getName().equals("net.muxigame.ysmcompat.production265.ServerDefaultReset"))throw new IllegalArgumentException("Approved production265 helper required");
        try{
            var call=helper.getClass().getMethod("resetAndReadback",MinecraftServer.class,ServerPlayer.class,UUID.class,ServerGamePacketListenerImpl.class);
            Class<?> result=call.getReturnType();if(!result.isRecord())throw new IllegalArgumentException("Invalid reset helper observation");
            var observed=result.getMethod("serverDefaultObserved");var attempted=result.getMethod("resetAttempted");var status=result.getMethod("status");
            if(observed.getReturnType()!=boolean.class||attempted.getReturnType()!=boolean.class||status.getReturnType()!=String.class)throw new IllegalArgumentException("Invalid reset helper profile");
            return new YsmProduction265Fallback(server,(s,p,id,listener)->{Object value=call.invoke(helper,s,p,id,listener);return new Observation((Boolean)observed.invoke(value),(Boolean)attempted.invoke(value),(String)status.invoke(value));});
        }catch(ReflectiveOperationException mismatch){throw new IllegalArgumentException("Invalid reset helper API",mismatch);}
    }
    @Override public boolean exactGenerationReady(long revision,String generation){return false;}
    @Override public boolean wear(YsmAssetCoordinator.Identity identity,String handle,String contentId,long revision,String generation){return false;}
    @Override public YsmAssetCoordinator.Fallback fallbackAndInvalidate(YsmAssetCoordinator.Identity expected,long revision,String generation){
        if(!server.isSameThread())return new YsmAssetCoordinator.Fallback(false,false,false);
        var player=server.getPlayerList().getPlayer(expected.playerId());
        if(player==null||player.server!=server||player.connection!=expected.listener()||!player.connection.isAcceptingMessages()||!player.getGameProfile().getName().equals(expected.uid())){
            last=new Observation(false,false,"STALE_OR_WRONG_THREAD");return new YsmAssetCoordinator.Fallback(false,false,false);
        }
        try{last=Objects.requireNonNull(resetter.reset(server,player,expected.playerId(),player.connection));}
        catch(Exception failure){last=new Observation(false,false,"RESET_OBSERVATION_FAILED");}
        boolean defaultObserved=server.getPlayerList().getPlayer(expected.playerId())==player&&player.connection==expected.listener()&&player.connection.isAcceptingMessages()&&player.getGameProfile().getName().equals(expected.uid())&&last.serverDefaultObserved()&&last.resetAttempted()&&last.status().equals("OBSERVED");
        return new YsmAssetCoordinator.Fallback(defaultObserved,false,false);
    }
    public Observation lastObservation(){if(!server.isSameThread())throw new IllegalStateException("Reset observation requires server thread");return last;}
}
