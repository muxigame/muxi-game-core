package net.muxigame.core.feature.tasks.integration;

import io.github.jamalam360.rightclickharvest.neoforge.RightClickHarvestNeoForgeEvents;
import net.muxigame.core.feature.tasks.GatheringTaskHooks;
import net.neoforged.bus.api.IEventBus;

/** Optional API bridge; never resolved on servers without RightClickHarvest. */
public final class RightClickHarvestTaskHooks {
    private RightClickHarvestTaskHooks() {}
    public static void register(GatheringTaskHooks hooks,IEventBus bus) {
        bus.addListener((RightClickHarvestNeoForgeEvents.AfterHarvest event)->
            hooks.rightClickHarvest(event.getContext().player(),event.getContext().block()));
    }
}
