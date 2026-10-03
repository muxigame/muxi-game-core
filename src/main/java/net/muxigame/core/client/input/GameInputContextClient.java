package net.muxigame.core.client.input;

import net.minecraft.client.Minecraft;
import net.muxigame.core.feature.input.GameInputContextState;
import net.muxigame.minigames.GameNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Owns the key request; Outbreak validates and performs the original interaction. */
@Mod(value="muxi_game_core",dist=Dist.CLIENT)
public final class GameInputContextClient {
    public GameInputContextClient(IEventBus ignored){
        GameplayInputPriority.registerPlainFAction("outbreak-equipment",()->{
            var mc=Minecraft.getInstance();var connection=mc.getConnection();
            return mc.player!=null&&mc.level!=null&&connection!=null&&NetworkRegistry.hasChannel(connection,GameNetwork.Action.TYPE.id())
                &&GameInputContextState.claimsOutbreak(mc.level.dimension().location().toString());
        },()->PacketDistributor.sendToServer(new GameNetwork.Action("outbreak","interact","")));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)->{GameInputContextState.reset();GameplayInputPriority.releaseAll();});
    }
}
