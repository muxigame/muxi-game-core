package net.muxigame.core.compat.mixin.xaeroworldmap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public interface GuiMapAccessor {
    @Accessor("mouseBlockDim") ResourceKey<Level> muxi$mouseBlockDim();
}
