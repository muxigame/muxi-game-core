package net.muxigame.core.client.input.mixin;

import net.minecraft.client.Options;
import net.muxigame.core.client.input.GlobalKeyBindingMigration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class GlobalOptionsInputMixin {
    // NeoForge invokes load(true) directly after RegisterKeyMappingsEvent.
    // The no-argument wrapper alone misses the modded binding restore pass.
    @Inject(method="load(Z)V",at=@At("RETURN"))
    private void muxi$migrate(boolean limited,CallbackInfo ci){GlobalKeyBindingMigration.apply((Options)(Object)this);}
}
