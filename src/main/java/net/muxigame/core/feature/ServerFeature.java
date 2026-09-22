package net.muxigame.core.feature;

import net.neoforged.bus.api.IEventBus;

/** One independently configured server-side integration owned by Game Core. */
public interface ServerFeature extends AutoCloseable {
    String id();
    void register(IEventBus gameBus);
    @Override void close();
}
