package net.muxigame.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.muxigame.core.feature.login.TerminalPassportNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/** The restricted bootstrap and PKCE verifier never enter an HTML page or game packet. */
public final class TerminalPassportApi {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String CREDENTIAL=System.getenv("MUXI_TERMINAL_GAME_CREDENTIAL");
    private static final String BROKER_PIPE=System.getenv("MUXI_TERMINAL_CREDENTIAL_PIPE");
    private static final String BROKER_SECRET=System.getenv("MUXI_TERMINAL_CREDENTIAL_BROKER");
    private static Pending pending;
    private record Pending(String id,String verifier,ClientPacketListener connection,Consumer<String> callback,long expires){
        @Override public String toString(){return "TerminalPassportPending[credentials=<redacted>]";}
    }
    private TerminalPassportApi() {}
    public static void register(IEventBus gameBus){
        TerminalPassportNetwork.clientReceiver(TerminalPassportApi::receive);
        gameBus.addListener(TerminalPassportApi::tick);
        gameBus.addListener(TerminalPassportApi::logout);
    }
    public static void request(Consumer<String> callback){
        Minecraft mc=Minecraft.getInstance();
        var connection=mc.getConnection();
        // Closing/reopening a terminal may leave an older native request awaiting timeout.
        if(pending!=null){callback.accept("");return;}
        if(mc.player==null || connection==null || (!TerminalCredentialBrokerClient.valid(BROKER_PIPE,BROKER_SECRET) && (CREDENTIAL==null || !CREDENTIAL.matches("[A-Za-z0-9_-]{43}")))
            || !NetworkRegistry.hasChannel(connection,TerminalPassportNetwork.Request.TYPE.id())){callback.accept("");return;}
        try {
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            String verifier=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            Pending request=new Pending(UUID.randomUUID().toString(),verifier,connection,callback,System.nanoTime()+18_000_000_000L);
            pending=request;

            // Every opening obtains a current short credential from the launcher. The game never refreshes an account token.
            var renewal=TerminalCredentialBrokerClient.valid(BROKER_PIPE,BROKER_SECRET)
                ? TerminalCredentialBrokerClient.fetch(BROKER_PIPE,BROKER_SECRET)
                : java.util.concurrent.CompletableFuture.completedFuture(CREDENTIAL);
            renewal.whenComplete((credential,error)->mc.execute(()->{
                if(pending!=request || mc.getConnection()!=connection)return;
                if(error!=null || credential==null || !credential.matches("[A-Za-z0-9_-]{43}")){finish("");return;}
                sendProof(request,challenge,credential);
            }));
        }catch(Exception ignored){pending=null;callback.accept("");}
    }
    private static void sendProof(Pending request,String challenge,String credential){
        Minecraft mc=Minecraft.getInstance();
        var connection=request.connection;
        try {
            JsonObject body=new JsonObject();body.addProperty("challenge",challenge);body.addProperty("requestId",request.id);
            HttpRequest httpRequest=HttpRequest.newBuilder(URI.create("https://account.muxigame.com/api/launcher/minecraft/terminal-proof"))
                .timeout(Duration.ofSeconds(3)).header("Authorization","MuxiTerminal "+credential)
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
            HTTP.sendAsync(httpRequest,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((response,error)->mc.execute(()->{
                if(pending!=request || mc.getConnection()!=connection) return;
                try {
                    if(error!=null || response.statusCode()!=200) throw new IllegalStateException();
                    String proof=JsonParser.parseString(response.body()).getAsJsonObject().get("proof").getAsString();
                    if(!proof.matches("[A-Za-z0-9_-]{43}")) throw new IllegalStateException();
                    PacketDistributor.sendToServer(new TerminalPassportNetwork.Request(request.id,proof));
                }catch(RuntimeException ignored){finish("");}
            }));
        }catch(Exception ignored){finish("");}
    }
    /** Cancel only the native terminal request; late HTTP/packet replies are ignored. */
    public static void cancel(){pending=null;}

    /** Check the launcher identity without navigating or exporting its account tokens. */
    public static void validate(Consumer<Boolean> callback){
        Minecraft mc=Minecraft.getInstance();var connection=mc.getConnection();
        if(mc.player==null || connection==null){callback.accept(false);return;}
        var renewal=TerminalCredentialBrokerClient.valid(BROKER_PIPE,BROKER_SECRET)
            ? TerminalCredentialBrokerClient.fetch(BROKER_PIPE,BROKER_SECRET)
            : java.util.concurrent.CompletableFuture.completedFuture(CREDENTIAL);
        renewal.thenCompose(credential->{
            if(credential==null || !credential.matches("[A-Za-z0-9_-]{43}"))throw new IllegalStateException();
            // The issuer checks source access-token expiry and revocation as well as bootstrap lifetime.
            JsonObject body=new JsonObject();body.addProperty("challenge","A".repeat(43));body.addProperty("requestId",UUID.randomUUID().toString());
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://account.muxigame.com/api/launcher/minecraft/terminal-proof"))
                .timeout(Duration.ofSeconds(3)).header("Authorization","MuxiTerminal "+credential).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
            return HTTP.sendAsync(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }).orTimeout(10,java.util.concurrent.TimeUnit.SECONDS).whenComplete((response,error)->mc.execute(()->{
            boolean valid=false;
            try{valid=error==null && mc.player!=null && mc.getConnection()==connection && response.statusCode()==200
                && JsonParser.parseString(response.body()).getAsJsonObject().get("proof").getAsString().matches("[A-Za-z0-9_-]{43}");}
            catch(RuntimeException ignored){}
            callback.accept(valid);
        }));
    }

    private static void receive(TerminalPassportNetwork.Result packet){
        if(pending==null || !pending.id.equals(packet.requestId()) || Minecraft.getInstance().getConnection()!=pending.connection) return;
        if(!packet.ticket().matches("[A-Za-z0-9_-]{43}")){finish("");return;}
        JsonObject payload=new JsonObject();payload.addProperty("ticket",packet.ticket());
        payload.addProperty("verifier",pending.verifier);payload.addProperty("requestId",pending.id);
        finish(payload.toString());
    }
    private static void finish(String value){var request=pending;pending=null;if(request!=null)request.callback.accept(value);}
    private static void tick(ClientTickEvent.Post event){if(pending!=null && System.nanoTime()>pending.expires)finish("");}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event){pending=null;}
}
