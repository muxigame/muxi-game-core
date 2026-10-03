package net.muxigame.binaryqa.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;import net.minecraft.client.Minecraft;import net.minecraft.network.protocol.game.*;import net.muxigame.binaryqa.*;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class) public class TraceChunkPackets {
 @Inject(method="handleLevelChunkWithLight",at=@At("HEAD")) private void begin(ClientboundLevelChunkWithLightPacket p,CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.begin("clientChunkPacket");var mc=Minecraft.getInstance();if(mc.player!=null&&p.getX()==mc.player.blockPosition().getX()>>4&&p.getZ()==mc.player.blockPosition().getZ()>>4)NativeProbe.mark("landingChunkReceiveBegin");}}
 @Inject(method="handleLevelChunkWithLight",at=@At("RETURN")) private void end(ClientboundLevelChunkWithLightPacket p,CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.end("clientChunkPacket");var mc=Minecraft.getInstance();if(mc.player!=null&&p.getX()==mc.player.blockPosition().getX()>>4&&p.getZ()==mc.player.blockPosition().getZ()>>4)NativeProbe.mark("landingChunkReceiveEnd");}}
 @Inject(method="handleMovePlayer",at=@At("HEAD")) private void position(CallbackInfo ci){if(Minecraft.getInstance().isSameThread())NativeProbe.mark("positionPacketBegin");}
 @Inject(method="handleMovePlayer",at=@At("RETURN")) private void positionEnd(CallbackInfo ci){if(Minecraft.getInstance().isSameThread())NativeProbe.mark("positionPacketEnd");}
 @Inject(method="handleUpdateRecipes",at=@At("HEAD")) private void recipes(CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.begin("recipesPacket");NativeProbe.mark("recipesPacketBegin");}}
 @Inject(method="handleUpdateRecipes",at=@At("RETURN")) private void recipesEnd(CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.end("recipesPacket");NativeProbe.mark("recipesPacketEnd");}}
 @Inject(method="handleUpdateTags",at=@At("HEAD")) private void tags(CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.begin("tagsPacket");NativeProbe.mark("tagsPacketBegin");}}
 @Inject(method="handleUpdateTags",at=@At("RETURN")) private void tagsEnd(CallbackInfo ci){if(Minecraft.getInstance().isSameThread()){Trace.end("tagsPacket");NativeProbe.mark("tagsPacketEnd");}}
 @Inject(method="handleChunkBatchFinished",at=@At("RETURN")) private void batch(CallbackInfo ci){if(Minecraft.getInstance().isSameThread())NativeProbe.mark("chunkBatchFinished");}
}
