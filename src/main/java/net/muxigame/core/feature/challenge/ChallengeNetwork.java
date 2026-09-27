package net.muxigame.core.feature.challenge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.function.Consumer;

public final class ChallengeNetwork {
    private ChallengeNetwork() {}
    private static Consumer<String> receiver=s->{};
    public static void receiver(Consumer<String> value) { receiver=value; }
    public record Action(String action,String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.parse("muxi_game_core:challenge_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.action,24);b.writeUtf(p.value,64);},b->new Action(b.readUtf(24),b.readUtf(64)));
        public Type<Action> type(){return TYPE;}
    }
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("muxi_game_core:challenge_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32767),b->new Snapshot(b.readUtf(32767)));
        public Type<Snapshot> type(){return TYPE;}
    }
    public static void register(IEventBus bus) {bus.addListener(ChallengeNetwork::registerPayloads);}
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("challenge-1").optional();
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,ctx)->receiver.accept(packet.json));
        r.playToServer(Action.TYPE,Action.CODEC,(packet,ctx)->{
            if(ctx.player() instanceof ServerPlayer p) {
                var service=ChallengeFeature.active(p.server);
                if(service!=null) service.handle(p,packet.action,packet.value);
            }
        });
    }
    public static void send(ServerPlayer p,String json) {
        if(p.connection!=null && NetworkRegistry.hasChannel(p.connection,Snapshot.TYPE.id()))
            PacketDistributor.sendToPlayer(p,new Snapshot(json));
    }
}
