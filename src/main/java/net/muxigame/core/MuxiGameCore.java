package net.muxigame.core;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.ServerFeature;
import net.muxigame.core.feature.champions.ChampionsFeature;
import net.muxigame.core.feature.chat.ChatCompletionFeature;
import net.muxigame.core.feature.identity.IdentityFeature;
import net.muxigame.core.feature.login.LoginGate;
import net.muxigame.core.feature.tasks.DailyTasksFeature;
import net.muxigame.core.feature.tasks.TaskNetwork;
import net.muxigame.core.feature.waystones.WaystoneMapNetwork;
import net.neoforged.bus.api.IEventBus;
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

    public MuxiGameCore(IEventBus modBus) {
        net.muxigame.core.threading.DimensionThreads.validate();
        net.muxigame.core.threading.ChunkTravel.register(NeoForge.EVENT_BUS);
        net.muxigame.core.feature.dimensions.WorldPortals.register(modBus);
        TaskNetwork.register(modBus);
        net.muxigame.core.feature.login.TerminalPassportNetwork.register(modBus);
        WaystoneMapNetwork.register(modBus);
        net.muxigame.core.feature.challenge.ChallengeNetwork.register(modBus);
        CoreConfig config = CoreConfig.load(Path.of(CoreConfig.FILE));
        // 先注册进服核验：它是这台服务器唯一的身份关口，出问题要第一时间在日志里看见。
        if (config.login().enabled()) register(new LoginGate(config.login()));
        if (config.identity().enabled()) {
            if (!ModList.get().isLoaded("simplenicknames"))
                throw new IllegalStateException("Game Core identity is enabled but Simple Nicknames 0.8.x is not installed.");
            register(new IdentityFeature(config.identity()));
        }
        // 玩法规则，不需要密钥和网络，装了 Champions 就生效；新生成的拦截在 ChampionSpawnHandlerMixin。
        if (ModList.get().isLoaded("champions")) register(new ChampionsFeature());
        // 普通聊天按 Tab 也能补在线玩家的昵称（原版只补 UID）；只发原版的补全包，不需要配置。
        if (ModList.get().isLoaded("simplenicknames")) register(new ChatCompletionFeature());
        register(new net.muxigame.core.feature.rules.CreeperTerrainProtection());
        register(new net.muxigame.core.feature.teleport.TpaFeature());
        register(new DailyTasksFeature());
        register(new net.muxigame.core.feature.dimensions.DimensionsFeature());
        register(new net.muxigame.core.feature.challenge.ChallengeFeature());
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
