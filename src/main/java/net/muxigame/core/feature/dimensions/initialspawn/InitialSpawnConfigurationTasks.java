package net.muxigame.core.feature.dimensions.initialspawn;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

/** No client packet extension: this task keeps the native connection in configuration. */
@EventBusSubscriber(modid = "muxi_game_core", bus = EventBusSubscriber.Bus.MOD)
public final class InitialSpawnConfigurationTasks {
    static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("muxi_game_core:initial_survival_preparation");
    private InitialSpawnConfigurationTasks() {}

    // Existing authentication tasks use NORMAL priority and must run before costly preparation.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterConfigurationTasksEvent event) {
        if (!(event.getListener() instanceof ServerConfigurationPacketListenerImpl listener)) return;
        event.register(new ConfigurationTask() {
            private final AtomicBoolean started = new AtomicBoolean();
            @Override public Type type() { return TYPE; }
            @Override public void start(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> sender) {
                if (!started.compareAndSet(false, true)) return;
                listener.getMainThreadEventLoop().execute(() -> {
                    if (listener.getMainThreadEventLoop() instanceof MinecraftServer server)
                        InitialSpawnPreparation.begin(server, listener);
                    else listener.disconnect(InitialSpawnPreparation.UNAVAILABLE);
                });
            }
        });
    }
}
