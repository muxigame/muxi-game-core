package net.muxigame.shadernative.mixin;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.screens.*;import net.minecraft.client.multiplayer.ServerData;import net.minecraft.client.multiplayer.resolver.ServerAddress;import net.muxigame.shadernative.ClientQA;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ConnectScreen.class) public class ConnectBoundary {
 @Inject(method="startConnecting",at=@At("HEAD"),require=1) private static void connecting(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,@Coerce Object transfer,CallbackInfo ci){ClientQA.connecting(address);}
}
