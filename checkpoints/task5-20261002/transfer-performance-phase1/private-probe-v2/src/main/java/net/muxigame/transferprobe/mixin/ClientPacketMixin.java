package net.muxigame.transferprobe.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.muxigame.transferprobe.ProbeWindow;
import net.muxigame.transferprobe.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketMixin {
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void probe$respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        String source = mc.level == null ? null : mc.level.dimension().location().toString();
        Trace.begin("client", this, source, packet.commonPlayerSpawnInfo().dimension().location().toString());
    }
    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void probe$respawnEnd(ClientboundRespawnPacket packet, CallbackInfo ci) { Trace.client("respawn_return"); }
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void probe$position(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        ProbeWindow w = Trace.client();
        if (w != null) { w.teleportId = packet.getId(); Trace.client("position_receive"); }
    }
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void probe$positionEnd(ClientboundPlayerPositionPacket packet, CallbackInfo ci) { Trace.client("position_return_after_ack"); }
    @Inject(method = "handleGameEvent", at = @At("RETURN"))
    private void probe$loadStart(ClientboundGameEventPacket packet, CallbackInfo ci) {
        if (packet.getEvent() == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START) Trace.client("load_start_receive");
    }
    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void probe$chunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.getX() == mc.player.chunkPosition().x
                && packet.getZ() == mc.player.chunkPosition().z) Trace.client("landing_chunk_receive");
    }
    @Inject(method = "handleChunkBatchFinished", at = @At("RETURN"))
    private void probe$batch(ClientboundChunkBatchFinishedPacket packet, CallbackInfo ci) {
        Trace.event("client", Trace.client(), "chunk_batch_receive", false, null, null);
    }
}
