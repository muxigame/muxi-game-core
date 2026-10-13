package net.muxigame.ysmcompat.production265;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.core.feature.ysmassets.*;
import net.muxigame.minigames.TrustedAccounts;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Original YSM transaction -> original Observation type -> Core pending fallback; native calls are fixtures. */
public final class YsmProductionFallbackTest {
    static int checks,calls;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static void denied(Runnable work,String why){boolean refused=false;try{work.run();}catch(IllegalArgumentException|IllegalStateException expected){refused=true;}check(refused,why);}
    static final class Port implements DefaultResetTransaction.Port {
        String mode="ok";int resets;
        public boolean current(){return true;}
        public DefaultResetTransaction.Selection defaultSelection(){return new DefaultResetTransaction.Selection(mode.equals("drift")&&resets>0?"changed":"builtin","default");}
        public boolean defaultAvailable(DefaultResetTransaction.Selection selected){return !mode.equals("missing");}
        public boolean acquireState(){return !mode.equals("no-state");}
        public void reset(){resets++;if(mode.equals("partial"))throw new IllegalStateException("explicit partial reset fixture");}
        public DefaultResetTransaction.Selection readState(){return new DefaultResetTransaction.Selection(mode.equals("mismatch")?"custom":"builtin","default");}
    }
    static YsmProduction265Fallback.Observation reset(Port port){
        calls++;var result=DefaultResetTransaction.execute(port);
        var original=new ServerDefaultReset.Observation(result.serverDefaultObserved(),result.resetAttempted(),result.status().name());
        return new YsmProduction265Fallback.Observation(original.serverDefaultObserved(),original.resetAttempted(),original.status());
    }
    public static void main(String[] args)throws Exception {
        var snapshot=YsmAssetSnapshot.parseTrusted(Files.readString(Path.of(args[0])));String uid="10000";var server=new MinecraftServer();var p=new ServerPlayer(server,uid,UUID.nameUUIDFromBytes(("OfflinePlayer:"+uid).getBytes(java.nio.charset.StandardCharsets.UTF_8)));TrustedAccounts.admitTerminal(p,10000);
        var connection=YsmTrustedPlayer.capture(p);var identity=connection.current();var port=new Port();var adapter=new YsmProduction265Fallback(server,(s,player,id,listener)->reset(port));
        check(!adapter.exactGenerationReady(snapshot.revision(),"g_"+"1".repeat(32)),"reset helper cannot enable exact generation");
        check(!adapter.wear(identity,"m_"+"1".repeat(32),"p_"+"1".repeat(32),snapshot.revision(),"g_"+"1".repeat(32)),"reset helper cannot wear models");
        var observed=adapter.fallbackAndInvalidate(identity,0,"");check(observed.defaultConfirmed()&&!observed.ownCacheInvalidated()&&!observed.trackingPeersAcknowledged()&&!observed.complete(),"server default observation is not cache/peer acknowledgement");
        check(adapter.lastObservation().status().equals("OBSERVED")&&port.resets==1,"original transaction and Observation mapped to Core");
        for(String failure:new String[]{"missing","no-state","partial","mismatch","drift"}){port.mode=failure;port.resets=0;var fallback=adapter.fallbackAndInvalidate(identity,0,"");check(!fallback.defaultConfirmed()&&!fallback.complete(),"original reset failure stays pending: "+failure);}
        port.mode="ok";port.resets=0;TrustedAccounts.revokeTerminal(p);check(adapter.fallbackAndInvalidate(identity,0,"").defaultConfirmed(),"safe default reset remains possible after Auth revocation on exact live transport");TrustedAccounts.admitTerminal(p,10000);
        var readyFixture=new YsmAssetCoordinator.RuntimePort(){public boolean exactGenerationReady(long r,String g){return true;}public boolean wear(YsmAssetCoordinator.Identity who,String m,String c,long r,String g){return true;}public YsmAssetCoordinator.Fallback fallbackAndInvalidate(YsmAssetCoordinator.Identity who,long r,String g){return adapter.fallbackAndInvalidate(who,r,g);}};
        var core=new YsmAssetCoordinator(server::isSameThread,readyFixture);core.stage(snapshot,"g_"+"1".repeat(32));String model=snapshot.visibleHandles(uid).stream().filter(m->m.startsWith("m_")).findFirst().orElseThrow();core.select(connection,model,snapshot.revision(),"explicit-native-wear-fixture");
        TrustedAccounts.revokeTerminal(p);check(core.reconcile().pendingFallbacks()==1,"server-default-only observation cannot clear Core pending revoke");
        TrustedAccounts.admitTerminal(p,10000);check(core.list(YsmTrustedPlayer.capture(p)).stream().noneMatch(YsmAssetCoordinator.Projection::canSelect),"pending cache/peer confirmation blocks new selection");denied(core::close,"close reports unconfirmed fallback");
        var wrong=new YsmAssetCoordinator.Identity("10001",identity.playerId(),identity.listener());int before=calls;check(!adapter.fallbackAndInvalidate(wrong,0,"").defaultConfirmed()&&calls==before,"wrong UID cannot reset actual player");
        AtomicReference<YsmAssetCoordinator.Fallback> offthread=new AtomicReference<>();Thread thread=new Thread(()->offthread.set(adapter.fallbackAndInvalidate(identity,0,"")));thread.start();thread.join(5000);check(!thread.isAlive()&&!offthread.get().defaultConfirmed()&&calls==before,"off-thread fallback never invokes reset");
        var during=new YsmProduction265Fallback(server,(s,player,id,listener)->{player.connection=new ServerGamePacketListenerImpl();return new YsmProduction265Fallback.Observation(true,true,"OBSERVED");});check(!during.fallbackAndInvalidate(identity,0,"").defaultConfirmed(),"post-reset listener change cannot be acknowledged");
        p.connection=(ServerGamePacketListenerImpl)identity.listener();var replacement=new ServerPlayer(server,uid,p.getUUID());check(!adapter.fallbackAndInvalidate(identity,0,"").defaultConfirmed()&&calls==before,"old identity cannot reset replacement actor");
        var throwing=new YsmProduction265Fallback(server,(s,player,id,listener)->{throw new java.io.IOException("fixture failure");});var replacementIdentity=new YsmAssetCoordinator.Identity(uid,replacement.getUUID(),replacement.connection);check(!throwing.fallbackAndInvalidate(replacementIdentity,0,"").complete()&&throwing.lastObservation().status().equals("RESET_OBSERVATION_FAILED"),"helper exception remains unconfirmed");
        denied(()->YsmProduction265Fallback.fromApprovedHelper(server,new Object()),"implicit or unrelated helper rejected");
        var method=ServerDefaultReset.class.getMethod("resetAndReadback",MinecraftServer.class,ServerPlayer.class,UUID.class,ServerGamePacketListenerImpl.class);check(method.getReturnType()==ServerDefaultReset.Observation.class&&method.getReturnType().isRecord(),"actual original helper API matches explicit adapter contract");
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"originalTransactionExercised\":true,\"originalHelperObservationTypeUsed\":true,\"mcListeners\":\"explicit fixtures\",\"helperNativeResetInvoked\":false,\"runtimeActivated\":false,\"corePendingFallbackCleared\":false}");
    }
}
