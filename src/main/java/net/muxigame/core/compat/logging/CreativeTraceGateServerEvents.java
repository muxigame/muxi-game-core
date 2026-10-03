package net.muxigame.core.compat.logging;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated servers have no client ticks; integrated servers use the client owner. */
@EventBusSubscriber(modid = "muxi_game_core", value = Dist.DEDICATED_SERVER)
public final class CreativeTraceGateServerEvents {
    private CreativeTraceGateServerEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CreativeTraceGate.update();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CreativeTraceGate.update();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CreativeTraceGate.shutdown();
    }
}
