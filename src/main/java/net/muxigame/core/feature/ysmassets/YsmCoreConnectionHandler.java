package net.muxigame.core.feature.ysmassets;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import net.minecraft.server.MinecraftServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Install in an approved HTTPS host; never starts a listener or loads credentials by itself. */
public final class YsmCoreConnectionHandler implements HttpHandler {
    public static final String PATH="/internal/ysm/connection/resolve";
    private final MinecraftServer server;private final YsmMachineAuthority authority;private final boolean isolatedLoopback;
    public YsmCoreConnectionHandler(MinecraftServer server,YsmMachineAuthority authority){this(server,authority,false);}
    private YsmCoreConnectionHandler(MinecraftServer server,YsmMachineAuthority authority,boolean test){this.server=Objects.requireNonNull(server);this.authority=Objects.requireNonNull(authority);isolatedLoopback=test;}
    /** Explicit test transport, still requires real MAC/use/audience/replay checks and loopback on both ends. */
    public static YsmCoreConnectionHandler forIsolatedLoopback(MinecraftServer server,YsmMachineAuthority authority){return new YsmCoreConnectionHandler(server,authority,true);}
    static String header(Headers h,String name){var values=h.get(name);if(values==null||values.size()!=1)throw new IllegalArgumentException();return values.getFirst();}
    static void reply(HttpExchange e,int code,JsonObject result)throws IOException{
        byte[] bytes=result.toString().getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.getResponseHeaders().set("Cache-Control","private, no-store");e.getResponseHeaders().set("X-Content-Type-Options","nosniff");e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);e.close();
    }
    static void fail(HttpExchange e,int status,String code)throws IOException{var j=new JsonObject();j.addProperty("detail",code);reply(e,status,j);}
    @Override public void handle(HttpExchange e)throws IOException{
        if(!(e instanceof HttpsExchange)&&!(isolatedLoopback&&e.getRemoteAddress().getAddress().isLoopbackAddress()&&e.getLocalAddress().getAddress().isLoopbackAddress())){fail(e,403,"tls_required");return;}
        if(!e.getRequestMethod().equals("POST")||!e.getRequestURI().getRawPath().equals(PATH)||e.getRequestURI().getRawQuery()!=null){fail(e,404,"not_found");return;}
        byte[] raw=e.getRequestBody().readNBytes(16385);if(raw.length>16384){fail(e,413,"wire_request_too_large");return;}
        if(!authority.configured()){fail(e,503,"ysm_game_authority_not_configured");return;}
        try{
            var h=e.getRequestHeaders();if(h.containsKey("Authorization")||h.containsKey("Cookie"))throw new IllegalArgumentException();
            var proof=new YsmMachineAuthority.Proof(header(h,"X-Muxi-Ysm-Server"),header(h,"X-Muxi-Ysm-Key"),header(h,"X-Muxi-Ysm-Purpose"),header(h,"X-Muxi-Ysm-Audience"),header(h,"X-Muxi-Ysm-Time"),header(h,"X-Muxi-Ysm-Nonce"),header(h,"X-Muxi-Ysm-Signature"));authority.verify(proof,"connection-resolve","POST",PATH,raw);
        }catch(YsmMachineAuthority.Unconfigured disabled){fail(e,503,"ysm_game_authority_not_configured");return;}
        catch(RuntimeException denied){fail(e,403,"invalid_machine_authority");return;}
        final YsmConnectionRegistry.Claim claim;
        try{
            var o=JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();if(!o.keySet().equals(Set.of("serverId","connectionId","bindingGeneration","uid")))throw new IllegalArgumentException();
            claim=new YsmConnectionRegistry.Claim(YsmAssetSnapshot.text(o,"serverId"),YsmAssetSnapshot.text(o,"connectionId"),YsmAssetSnapshot.text(o,"bindingGeneration"),YsmAssetSnapshot.uid(YsmAssetSnapshot.text(o,"uid")));
        }catch(RuntimeException malformed){fail(e,400,"invalid_game_context");return;}
        var future=new CompletableFuture<YsmAssetCoordinator.Identity>();server.execute(()->{try{future.complete(YsmAuthBindings.resolve(server,claim));}catch(RuntimeException expired){future.completeExceptionally(expired);}});
        try{
            var who=future.get(3,TimeUnit.SECONDS);var result=new JsonObject();result.addProperty("serverId",claim.serverId());result.addProperty("connectionId",claim.connectionId());result.addProperty("bindingGeneration",claim.bindingGeneration());result.addProperty("uid",who.uid());result.addProperty("live",true);reply(e,200,result);
        }catch(ExecutionException expired){if(expired.getCause() instanceof YsmConnectionRegistry.UidMismatch)fail(e,403,"uid_claim_mismatch");else fail(e,409,"game_connection_changed");}
        catch(TimeoutException timeout){future.cancel(false);fail(e,503,"game_connection_lookup_unavailable");}
        catch(InterruptedException cancelled){Thread.currentThread().interrupt();future.cancel(false);fail(e,503,"game_connection_lookup_unavailable");}
    }
}
