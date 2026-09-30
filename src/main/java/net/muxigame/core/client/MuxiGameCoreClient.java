package net.muxigame.core.client;

import net.muxigame.core.MuxiGameCore;
import net.muxigame.core.nickname.Nicknames;
import net.muxigame.core.client.tasks.DailyTasksClient;
import net.muxigame.core.feature.tasks.DailyTasksFeature;
import net.muxigame.core.feature.tasks.TaskNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口：昵称显示兼容，以及每日任务 HUD。单人游戏只启用本地每日任务服务，
 * 不启用专用服务器的登录核验、昵称同步和强敌规则。
 */
@Mod(value = MuxiGameCore.MOD_ID, dist = Dist.CLIENT)
public final class MuxiGameCoreClient {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/client");

    public MuxiGameCoreClient(IEventBus modBus) {
        net.muxigame.core.feature.dimensions.WorldPortals.register(modBus);
        TaskNetwork.register(modBus);
        net.muxigame.core.feature.challenge.ChallengeNetwork.register(modBus);
        new net.muxigame.core.feature.challenge.ChallengeFeature().register(NeoForge.EVENT_BUS);
        net.muxigame.core.client.challenge.ChallengeClient.register(modBus,NeoForge.EVENT_BUS);
        new DailyTasksFeature().register(NeoForge.EVENT_BUS);
        new net.muxigame.core.feature.dimensions.DimensionsFeature().register(NeoForge.EVENT_BUS);
        DailyTasksClient.register(modBus,NeoForge.EVENT_BUS);
        LOG.info("muxi Game Core client loaded; nickname display {}",
                 Nicknames.available() ? "enabled" : "disabled (Simple Nicknames not installed)");
    }
}
