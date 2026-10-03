package net.muxigame.core.feature.login;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

/** The game wire carries public correlation UUIDs; account access tokens stay in native HTTPS. */
public final class TerminalPassportNetwork {
    private static Consumer<Result> receiver=packet->{};
    private TerminalPassportNetwork() {}
    public static void clientReceiver(Consumer<Result> value){receiver=value;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("muxi_game_core",path);}
    public record Request(String requestId,String gameSession,int action) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(id("terminal_access_request_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeUtf(p.gameSession,36);b.writeVarInt(p.action);},
            b->new Request(b.readUtf(36),b.readUtf(36),b.readVarInt()));
        @Override public Type<Request> type(){return TYPE;}
        @Override public String toString(){return "TerminalAccessRequest[public connection correlation]";}
    }
    public record Result(String requestId,String gameSession,long uid,int status) implements CustomPacketPayload {
        public static final Type<Result> TYPE=new Type<>(id("terminal_access_result_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Result> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.requestId,36);b.writeUtf(p.gameSession,36);b.writeVarLong(p.uid);b.writeVarInt(p.status);},
            b->new Result(b.readUtf(36),b.readUtf(36),b.readVarLong(),b.readVarInt()));
        @Override public Type<Result> type(){return TYPE;}
        @Override public String toString(){return "TerminalAccessResult[public connection correlation]";}
    }
    public static void register(IEventBus bus){bus.addListener(TerminalPassportNetwork::payloads);}
    private static void payloads(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("terminal-access-2").optional();
        registrar.playToClient(Result.TYPE,Result.CODEC,(p,c)->receiver.accept(p));
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->{
            if(c.player() instanceof ServerPlayer player)TerminalPassportServer.request(player,p);
        });
    }
}
