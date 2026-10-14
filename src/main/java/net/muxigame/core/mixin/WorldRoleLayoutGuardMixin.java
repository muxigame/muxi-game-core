package net.muxigame.core.mixin;
import net.minecraft.server.MinecraftServer;
import net.muxigame.core.feature.dimensions.WorldRoleLayoutGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class)
public abstract class WorldRoleLayoutGuardMixin {
 @Inject(method="createLevels",at=@At("HEAD"))private void muxi$verifyRoles(CallbackInfo ci){WorldRoleLayoutGuard.verify((MinecraftServer)(Object)this);}
}
