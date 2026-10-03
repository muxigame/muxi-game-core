package net.muxigame.core.compat.logging;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "muxi_game_core", value = Dist.CLIENT)
public final class CreativeTraceGateClientEvents {
    private CreativeTraceGateClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CreativeTraceGate.update();
    }
}
