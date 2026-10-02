package net.muxigame.core.feature.login;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

/** Only a short PKCE-bound proof crosses the game connection, never a launcher credential. */
public final class TerminalPassportNetwork {
    private static Consumer<Result> receiver = packet -> {};
    private TerminalPassportNetwork() {}
    public static void clientReceiver(Consumer<Result> value) { receiver=value; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("muxi_game_core",path); }
    public record Request(String requestId, String proof) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(id("terminal_passport_request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeUtf(p.proof,43);},
            b->new Request(b.readUtf(36),b.readUtf(43)));
        @Override public Type<Request> type(){return TYPE;}
        @Override public String toString(){return "TerminalPassportRequest[credentials=<redacted>]";}
    }
    public record Result(String requestId, String ticket) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(id("terminal_passport_result_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeUtf(p.ticket,43);},
            b->new Result(b.readUtf(36),b.readUtf(43)));
        @Override public Type<Result> type(){return TYPE;}
        @Override public String toString(){return "TerminalPassportResult[credentials=<redacted>]";}
    }
    public static void register(IEventBus bus){bus.addListener(TerminalPassportNetwork::payloads);}
    private static void payloads(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("terminal-passport-1").optional();
        registrar.playToClient(Result.TYPE,Result.CODEC,(p,c)->receiver.accept(p));
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->{
            if(c.player() instanceof ServerPlayer player) TerminalPassportServer.request(player,p);
        });
    }
}
