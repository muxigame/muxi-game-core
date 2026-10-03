package net.muxigame.core.feature.input;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="muxi_game_core",bus=EventBusSubscriber.Bus.MOD)
public final class GameInputContextNetwork {
    private GameInputContextNetwork() {}
    public record Context(String game,String dimension) implements CustomPacketPayload {
        public static final Type<Context> TYPE=new Type<>(ResourceLocation.parse("muxi_game_core:input_context"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Context> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.game,32);b.writeUtf(p.dimension,128);},b->new Context(b.readUtf(32),b.readUtf(128)));
        public Type<Context> type(){return TYPE;}
    }
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("core-input-1").optional().playToClient(Context.TYPE,Context.CODEC,
            (p,c)->c.enqueueWork(()->GameInputContextState.accept(p.game,p.dimension)));
    }
}
