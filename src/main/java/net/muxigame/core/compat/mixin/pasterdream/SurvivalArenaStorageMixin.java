package net.muxigame.core.compat.mixin.pasterdream;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The mod's one-arena claim must belong to the world being generated, not always home. */
@Mixin(targets="com.pasterdream.pasterdreammod.worldgen.structure.AaroncosArenaPortalStructure",remap=false)
public abstract class SurvivalArenaStorageMixin {
    @Redirect(method="findGenerationPoint",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel muxi$ownArenaClaim(MinecraftServer server,ResourceKey<Level> key,Structure.GenerationContext context) {
        if(key.equals(Level.OVERWORLD)) for(var dimension:WorldDimensions.EXPLORATION) {
            ServerLevel survival=server.getLevel(dimension);
            if(survival!=null&&survival.getChunkSource().getGenerator()==context.chunkGenerator())return survival;
        }
        return server.getLevel(key);
    }
}
