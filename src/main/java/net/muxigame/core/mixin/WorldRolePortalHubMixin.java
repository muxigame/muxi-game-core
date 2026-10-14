package net.muxigame.core.mixin;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.muxigame.core.feature.dimensions.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Offline role state chooses the portal hub; no global dimension alias or inventory rewrite. */
@Mixin({NetherPortalBlock.class,EndPortalBlock.class})
public abstract class WorldRolePortalHubMixin {
 @WrapOperation(method="getPortalDestination",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))private ServerLevel muxi$hub(MinecraftServer server,ResourceKey<Level> key,Operation<ServerLevel> original){return original.call(server,key.equals(Level.OVERWORLD)&&WorldRoleState.get(server).homeHub()?WorldDimensions.HOME:key);}
}
