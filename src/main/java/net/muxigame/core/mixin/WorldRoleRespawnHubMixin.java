package net.muxigame.core.mixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.muxigame.core.feature.dimensions.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Valid saved beds/anchors are handled by vanilla first. Only its no/invalid-bed fallback hub changes. */
@Mixin(ServerPlayer.class)
public abstract class WorldRoleRespawnHubMixin {
 @WrapOperation(method="findRespawnPositionAndUseSpawnBlock",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;overworld()Lnet/minecraft/server/level/ServerLevel;"))private ServerLevel muxi$fallbackHub(MinecraftServer server,Operation<ServerLevel> original){return WorldRoleState.get(server).homeHub()?server.getLevel(WorldDimensions.HOME):original.call(server);}
}
