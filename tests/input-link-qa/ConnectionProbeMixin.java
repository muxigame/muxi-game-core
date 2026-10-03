package net.muxigame.inputlinkqa.mixin;
import net.muxigame.inputlinkqa.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.muxigame.minigames.GameNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class ConnectionProbeMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void probe(Packet<?> packet,CallbackInfo ci){if(!ProbeFiles.enabled())return;if(packet instanceof ServerboundCustomPayloadPacket p&&p.payload() instanceof GameNetwork.Action a&&a.game().equals("outbreak")&&a.action().equals("interact")&&a.value().isEmpty())ProbeCounters.sent.incrementAndGet();if(packet instanceof ServerboundPlayerActionPacket p&&p.getAction()==ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)ProbeCounters.swap.incrementAndGet();}
}
