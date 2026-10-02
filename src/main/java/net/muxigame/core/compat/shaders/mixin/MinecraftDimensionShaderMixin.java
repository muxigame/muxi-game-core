package net.muxigame.core.compat.shaders.mixin;
import net.muxigame.core.compat.shaders.DimensionShaderSwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Minecraft.class,priority=2000)
public class MinecraftDimensionShaderMixin {
    @Inject(method="setLevel",at=@At("HEAD"))
    private void muxi$changing(ClientLevel level,ReceivingLevelScreen.Reason reason,CallbackInfo ci){DimensionShaderSwap.changingLevel(level);}
    // Both disconnect and server reconfiguration call this common teardown path.
    // Minecraft.disconnect does not call clearClientLevel in 1.21.1.
    @Inject(method="updateLevelInEngines",at=@At("RETURN"))
    private void muxi$leaving(ClientLevel level,CallbackInfo ci){if(level==null)DimensionShaderSwap.clear();}
}
