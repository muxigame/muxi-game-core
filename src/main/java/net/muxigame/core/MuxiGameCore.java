package net.muxigame.core;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.identity.IdentityFeature;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** General server integration host. Add gameplay features here, not to the launcher. */
@Mod(value = MuxiGameCore.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class MuxiGameCore {
    public static final String MOD_ID = "muxi_game_core";
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core");
    private final List<ServerFeature> features = new ArrayList<>();

    public MuxiGameCore() {
        CoreConfig config = CoreConfig.load(Path.of(CoreConfig.FILE));
        if (config.identity().enabled()) {
            if (!ModList.get().isLoaded("simplenicknames"))
                throw new IllegalStateException("Game Core identity is enabled but Simple Nicknames 0.8.x is not installed.");
            register(new IdentityFeature(config.identity()));
        }
        // Future integrations implement ServerFeature and get their own config section.
        NeoForge.EVENT_BUS.addListener(this::onStopped);
        LOG.info("muxi Game Core loaded; enabled features: {}", features.stream().map(ServerFeature::id).toList());
    }

    private void register(ServerFeature feature) {
        if (features.stream().anyMatch(existing -> existing.id().equals(feature.id())))
            throw new IllegalStateException("Duplicate Game Core feature: " + feature.id());
        feature.register(NeoForge.EVENT_BUS);
        features.add(feature);
    }

    private void onStopped(ServerStoppedEvent event) {
        for (int i = features.size() - 1; i >= 0; i--) {
            try { features.get(i).close(); }
            catch (RuntimeException ignored) { LOG.warn("Feature {} failed to close cleanly", features.get(i).id()); }
        }
        features.clear();
    }
}
