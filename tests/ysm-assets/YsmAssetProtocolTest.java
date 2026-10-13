package net.muxigame.core.feature.ysmassets;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.muxigame.minigames.TrustedAccounts;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Two deterministic native-runtime stand-ins; not live Minecraft or YSM format acceptance. */
public final class YsmAssetProtocolTest {
    static int checks;static Map<String,JsonObject> cases=new HashMap<>();static String a,b,p,sha;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void deny(Runnable action){checks++;try{action.run();throw new AssertionError("Expected denial at "+checks);}catch(IllegalArgumentException|IllegalStateException expected){}}
    static UUID uuid(String uid){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+uid).getBytes(StandardCharsets.UTF_8));}
    static ServerPlayer player(MinecraftServer s,String uid){var p=new ServerPlayer(s,uid,uuid(uid));TrustedAccounts.admitTerminal(p,Long.parseLong(uid));return p;}
    static YsmAssetSnapshot state(String name){return YsmAssetSnapshot.parseTrusted(cases.get(name).get("snapshot").toString());}
    static String generation(YsmAssetSnapshot s){return "g_"+String.format("%032x",s.revision());}
    static final class NativeFixture implements YsmAssetCoordinator.RuntimePort {
        long revision=-1;String gen="";boolean cache=true,peers=true,wear=true;int writes,resets;Runnable duringWear;
        final Map<Object,String> worn=new IdentityHashMap<>();final List<Object> resetListeners=new ArrayList<>();
        void ready(YsmAssetSnapshot s){revision=s.revision();gen=generation(s);}
        @Override public boolean exactGenerationReady(long r,String g){return r==revision&&g.equals(gen);}
        @Override public boolean wear(YsmAssetCoordinator.Identity actor,String handle,String content,long r,String g){
            writes++;if(duringWear!=null){var action=duringWear;duringWear=null;action.run();}worn.put(actor.listener(),handle);return wear;
        }
        @Override public YsmAssetCoordinator.Fallback fallbackAndInvalidate(YsmAssetCoordinator.Identity who,long r,String g){
            resets++;resetListeners.add(who.listener());worn.put(who.listener(),"builtin_default");return new YsmAssetCoordinator.Fallback(true,cache,peers);
        }
    }
    static void stage(YsmAssetCoordinator core,NativeFixture runtime,String name){var s=state(name);var receipt=core.stage(s,generation(s));check(!receipt.active());runtime.ready(s);}
    static void contracts(){
        var q=state("privateA");check(q.contents().size()==1);check(q.references().size()==1);check(q.visibleHandles("10001").equals(List.of(a)));check(q.visibleHandles("10002").isEmpty());
        check(state("duplicateA").equals(q));check(state("sharedPrivate").contents().size()==1);check(state("sharedPrivate").references().size()==2);
        check(state("deletedA").visibleHandles("10001").isEmpty());check(state("deletedA").visibleHandles("10002").equals(List.of(b)));check(state("deletedBoth").contents().isEmpty());
        for(String badUid:new String[]{"1000","010001","name","1e4","-10001"})deny(()->q.visibleHandles(badUid));
        var corrupt=cases.get("sharedPrivate").getAsJsonObject("snapshot").deepCopy();corrupt.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("ownerUids").remove(0);deny(()->YsmAssetSnapshot.parseTrusted(corrupt.toString()));
        var badSelection=cases.get("privateA").getAsJsonObject("snapshot").deepCopy();var row=new JsonObject();row.addProperty("uid","10002");row.addProperty("modelId",a);row.addProperty("contentId",p);badSelection.getAsJsonArray("selections").add(row);deny(()->YsmAssetSnapshot.parseTrusted(badSelection.toString()));
        for(String key:List.of("active","selectEnabled")){var bad=cases.get("privateA").getAsJsonObject("snapshot").deepCopy();bad.addProperty(key,true);deny(()->YsmAssetSnapshot.parseTrusted(bad.toString()));}
        var path=cases.get("privateA").getAsJsonObject("snapshot").deepCopy();path.getAsJsonArray("models").get(0).getAsJsonObject().addProperty("storageKey","../auth/private.ysm");deny(()->YsmAssetSnapshot.parseTrusted(path.toString()));
        var duplicate=cases.get("sharedPrivate").getAsJsonObject("snapshot").deepCopy();var content=duplicate.getAsJsonArray("models").get(0).getAsJsonObject();content.getAsJsonArray("references").add(content.getAsJsonArray("references").get(0).deepCopy());deny(()->YsmAssetSnapshot.parseTrusted(duplicate.toString()));
    }
    static void rights(){
        var server=new MinecraftServer();var pa=player(server,"10001");var pb=player(server,"10002");var ca=YsmTrustedPlayer.capture(pa);var cb=YsmTrustedPlayer.capture(pb);var nativePort=new NativeFixture();var core=new YsmAssetCoordinator(server::isSameThread,nativePort);
        var privateState=state("privateA");core.stage(privateState,generation(privateState));check(!core.list(ca).get(0).canSelect());deny(()->core.select(ca,a,core.revision(),"owner-key-1"));nativePort.ready(privateState);
        check(core.list(cb).isEmpty());deny(()->core.metadata(cb,a,core.revision()));deny(()->core.downloadPermit(cb,a,core.revision()));deny(()->core.downloadPermit(cb,sha,core.revision()));deny(()->core.downloadPermit(cb,p,core.revision()));
        var own=core.downloadPermit(ca,a,core.revision());core.validatePermit(ca,own);check(own.identity().uid().equals("10001"));check(!core.canReceivePrivateRenderBytes(cb,a,core.revision()));
        core.select(ca,a,core.revision(),"owner-key-2");int writes=nativePort.writes;core.select(ca,a,core.revision(),"owner-key-2");check(nativePort.writes==writes);deny(()->core.select(ca,"m_"+"f".repeat(32),core.revision(),"owner-key-2"));
        check(core.applyDesiredSelection(cb).isEmpty());stage(core,nativePort,"sharedPrivate");deny(()->core.validatePermit(ca,own));check(core.list(cb).stream().map(YsmAssetCoordinator.Projection::modelId).toList().equals(List.of(b)));deny(()->core.metadata(cb,a,core.revision()));deny(()->core.metadata(ca,b,core.revision()));
        stage(core,nativePort,"publicA");check(core.list(cb).stream().anyMatch(x->x.modelId().equals(p)));check(new Gson().toJson(core.metadata(cb,p,core.revision())).indexOf(sha)<0);var publicPermit=core.select(cb,p,core.revision(),"public-key-1");check(core.canReceivePrivateRenderBytes(cb,p,core.revision()));
        nativePort.cache=false;var rev=state("privateAgain");var receipt=core.stage(rev,generation(rev));nativePort.ready(rev);check(receipt.pendingFallbacks()==1);check(nativePort.worn.get(pb.connection).equals("builtin_default"));deny(()->core.validatePermit(cb,publicPermit));deny(()->core.select(cb,p,core.revision(),"public-key-1"));check(!core.list(ca).get(0).canSelect());
        nativePort.cache=true;check(core.reconcile().pendingFallbacks()==0);var privateB=core.downloadPermit(cb,b,core.revision());core.select(cb,b,core.revision(),"private-b-new");
        stage(core,nativePort,"deletedA");check(core.list(ca).isEmpty());deny(()->core.downloadPermit(ca,a,core.revision()));check(core.list(cb).size()==1);check(nativePort.worn.get(pa.connection).equals("builtin_default"));check(nativePort.worn.get(pb.connection).equals(b));
        stage(core,nativePort,"deletedBoth");check(core.list(cb).isEmpty());check(nativePort.worn.get(pb.connection).equals("builtin_default"));deny(()->core.validatePermit(cb,privateB));
        stage(core,nativePort,"restoredA");check(core.list(cb).isEmpty());deny(()->core.metadata(cb,p,core.revision()));check(core.list(ca).size()==1);deny(()->core.stage(state("privateA"),generation(state("privateA"))));
        var restored=state("restoredA");check(!core.stage(restored,generation(restored)).active());deny(()->core.stage(restored,"g_"+"f".repeat(32)));core.close();deny(()->core.list(ca));
        var inert=new YsmAssetCoordinator(server::isSameThread);inert.stage(privateState,generation(privateState));deny(()->inert.downloadPermit(ca,a,privateState.revision()));deny(()->inert.select(ca,a,privateState.revision(),"never-active"));inert.close();
    }
    static void identities()throws Exception{
        var server=new MinecraftServer();var pa=player(server,"10001");var pb=player(server,"10002");var ca=YsmTrustedPlayer.capture(pa);var cb=YsmTrustedPlayer.capture(pb);var port=new NativeFixture();var core=new YsmAssetCoordinator(server::isSameThread,port);stage(core,port,"sharedPrivate");
        var permit=core.select(ca,a,core.revision(),"conn-key-old");var old=ca.current();var replacement=player(server,"10001");var fresh=YsmTrustedPlayer.capture(replacement);deny(()->core.validatePermit(ca,permit));var next=core.select(fresh,a,core.revision(),"conn-key-new");core.reconcile();check(!port.resetListeners.contains(replacement.connection));core.disconnect(old);TrustedAccounts.revokeTerminal(pa);core.validatePermit(fresh,next);
        TrustedAccounts.revokeTerminal(replacement);deny(()->core.list(fresh));core.reconcile();check(port.worn.get(replacement.connection).equals("builtin_default"));check(port.worn.get(pb.connection)==null);
        var fake=new ServerPlayer(new MinecraftServer(),"10001",uuid("10001"));deny(()->YsmTrustedPlayer.capture(fake));
        final boolean[] wrongThread={false};var worker=new Thread(()->{try{YsmTrustedPlayer.capture(pb);}catch(IllegalArgumentException e){wrongThread[0]=true;}});worker.start();worker.join();check(wrongThread[0]);
        var oldB=cb.current();pb.connection=new ServerGamePacketListenerImpl();deny(()->cb.current());TrustedAccounts.admitTerminal(pb,10002);var newB=YsmTrustedPlayer.capture(pb);var nextB=core.select(newB,b,core.revision(),"new-b-listener");core.disconnect(oldB);core.validatePermit(newB,nextB);core.close();check(port.worn.get(pb.connection).equals("builtin_default"));
    }
    static void desiredSelection(){
        var server=new MinecraftServer();var pa=player(server,"10001");var pb=player(server,"10002");var ca=YsmTrustedPlayer.capture(pa);var cb=YsmTrustedPlayer.capture(pb);var port=new NativeFixture();var core=new YsmAssetCoordinator(server::isSameThread,port);
        var export=cases.get("privateA").getAsJsonObject("snapshot").deepCopy();var row=new JsonObject();row.addProperty("uid","10001");row.addProperty("modelId",a);row.addProperty("contentId",p);export.getAsJsonArray("selections").add(row);var s=YsmAssetSnapshot.parseTrusted(export.toString());core.stage(s,generation(s));deny(()->core.applyDesiredSelection(ca));port.ready(s);check(core.applyDesiredSelection(ca).isPresent());check(core.applyDesiredSelection(cb).isEmpty());check(port.worn.get(pa.connection).equals(a));check(port.worn.get(pb.connection)==null);core.close();
    }
    static void races(){
        var server=new MinecraftServer();var pb=player(server,"10002");var cb=YsmTrustedPlayer.capture(pb);var port=new NativeFixture();var core=new YsmAssetCoordinator(server::isSameThread,port);stage(core,port,"publicA");
        port.duringWear=()->{var next=state("privateAgain");core.stage(next,generation(next));port.ready(next);};deny(()->core.select(cb,p,state("publicA").revision(),"race-public-wear"));check(port.worn.get(pb.connection).equals("builtin_default"));check(core.list(cb).stream().noneMatch(x->x.modelId().equals(p)));
        core.close();
        var failed=new NativeFixture();var other=new YsmAssetCoordinator(server::isSameThread,failed);stage(other,failed,"sharedPrivate");failed.wear=false;failed.peers=false;deny(()->other.select(cb,b,other.revision(),"wear-refused"));check(other.reconcile().pendingFallbacks()==1);check(!other.list(cb).get(0).canSelect());failed.peers=true;check(other.reconcile().pendingFallbacks()==0);other.close();
    }
    static void wireBindings(){
        var server=new MinecraftServer();var pa=player(server,"10001");var registry=new YsmConnectionRegistry(server,"fixture-server");var claim=registry.bindVerified(pa);var oldConnection=registry.resolve(claim);check(oldConnection.current().uid().equals("10001"));check(registry.claimFor(pa).equals(claim));check(registry.connectionFor(pa).current().uid().equals("10001"));var pb=player(server,"10002");deny(()->registry.claimFor(pb));registry.bindVerified(pb);check(registry.claimFor(pb).uid().equals("10002"));
        deny(()->registry.resolve(new YsmConnectionRegistry.Claim("other-server",claim.connectionId(),claim.bindingGeneration(),claim.uid())));
        deny(()->registry.resolve(new YsmConnectionRegistry.Claim(claim.serverId(),claim.connectionId(),claim.bindingGeneration(),"10002")));
        deny(()->registry.resolve(new YsmConnectionRegistry.Claim(claim.serverId(),claim.connectionId(),"old-generation",claim.uid())));
        var oldIdentity=oldConnection.current();var next=registry.bindVerified(pa);check(!oldConnection.fallbackTargetCurrent(oldIdentity));check(!claim.bindingGeneration().equals(next.bindingGeneration()));deny(()->registry.resolve(claim));deny(()->oldConnection.current());registry.retire(claim);check(registry.resolve(next).current().uid().equals("10001"));
        TrustedAccounts.revokeTerminal(pa);registry.prune();deny(()->registry.resolve(next));TrustedAccounts.admitTerminal(pa,10001);var renewed=registry.bindVerified(pa);check(registry.resolve(renewed).current().uid().equals("10001"));deny(()->registry.resolve(next));registry.close();deny(()->registry.resolve(renewed));
    }
    public static void main(String[] args)throws Exception{
        var root=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();a=root.get("assetA").getAsString();b=root.get("assetB").getAsString();p=root.get("contentId").getAsString();sha=root.get("sha256").getAsString();for(var row:root.getAsJsonArray("cases")){var value=row.getAsJsonObject();cases.put(value.get("name").getAsString(),value);}
        contracts();rights();identities();desiredSelection();races();wireBindings();System.out.println("{\"passed\":true,\"checks\":"+checks+",\"realWebStoreSnapshots\":true,\"identitySource\":\"actual TrustedAccounts with explicit MC listener fixtures\",\"nativePort\":\"synthetic protocol only\",\"productionActivated\":false,\"minecraftStarted\":false}");
    }
}
