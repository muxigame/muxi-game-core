package net.muxigame.core.client;

import net.muxigame.core.MuxiGameCore;
import net.muxigame.core.nickname.Nicknames;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口。客户端这边目前只有显示层的兼容（把 UID 显示成昵称），全部由 compat 下的 mixin 完成；
 * 服务端功能（登录核验、昵称同步、强敌规则）只在 {@link MuxiGameCore} 里，客户端不加载。
 */
@Mod(value = MuxiGameCore.MOD_ID, dist = Dist.CLIENT)
public final class MuxiGameCoreClient {
    private static final Logger LOG = LoggerFactory.getLogger("muxi-game-core/client");

    public MuxiGameCoreClient() {
        LOG.info("muxi Game Core client loaded; nickname display {}",
                 Nicknames.available() ? "enabled" : "disabled (Simple Nicknames not installed)");
    }
}
