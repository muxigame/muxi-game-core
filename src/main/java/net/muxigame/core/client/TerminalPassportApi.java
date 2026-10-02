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
        if(mc.player==null || connection==null || CREDENTIAL==null || !CREDENTIAL.matches("[A-Za-z0-9_-]{43}")
            || !NetworkRegistry.hasChannel(connection,TerminalPassportNetwork.Request.TYPE.id())){callback.accept("");return;}
        try {
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            String verifier=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            Pending request=new Pending(UUID.randomUUID().toString(),verifier,connection,callback,System.nanoTime()+8_000_000_000L);
            pending=request;
            JsonObject body=new JsonObject();body.addProperty("challenge",challenge);body.addProperty("requestId",request.id);
            HttpRequest httpRequest=HttpRequest.newBuilder(URI.create("https://account.muxigame.com/api/launcher/minecraft/terminal-proof"))
                .timeout(Duration.ofSeconds(3)).header("Authorization","MuxiTerminal "+CREDENTIAL)
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
        }catch(Exception ignored){pending=null;callback.accept("");}
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
