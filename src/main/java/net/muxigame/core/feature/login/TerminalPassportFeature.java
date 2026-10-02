package net.muxigame.core.feature.login;

import net.muxigame.core.config.CoreConfig;
import net.muxigame.core.feature.ServerFeature;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Optional SSO transport binding. Never verifies/refuses a game join or grants game privileges. */
public final class TerminalPassportFeature implements ServerFeature {
    public TerminalPassportFeature(CoreConfig.TerminalSso config){TerminalPassportServer.configure(config);}
    @Override public String id(){return "terminalSso";}
    @Override public void register(IEventBus gameBus){gameBus.addListener(this::loggedIn);gameBus.addListener(this::loggedOut);}
    private void loggedIn(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer))TerminalPassportServer.admit(player);
    }
    private void loggedOut(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer) && player.connection!=null)TerminalPassportServer.disconnect(player);
    }
    @Override public void close(){TerminalPassportServer.close();}
}
