package net.muxigame.core.threading.mixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
@Mixin(MinecraftServer.class)
public interface ServerTimingAccess {
    @Accessor("perWorldTickTimes") Map<ResourceKey<Level>,long[]> muxi$worldTimes();
}
