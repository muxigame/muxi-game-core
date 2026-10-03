package net.muxigame.core.feature.rules;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Adventure-only terrain protection; entity damage and other dimensions retain their rules. */
@EventBusSubscriber(modid="muxi_game_core")
public final class AdventureEnvironmentProtection {
    private AdventureEnvironmentProtection() {}

    public static boolean protectedLevel(Level level) {
        return !level.isClientSide && WorldDimensions.ADVENTURE.equals(level.dimension());
    }

    public static boolean rejectFire(Level level, BlockState state) {
        return protectedLevel(level) && (state.is(BlockTags.FIRE) || state.getBlock() instanceof BaseFireBlock);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (protectedLevel(event.getLevel())) event.getAffectedBlocks().clear();
        // Do not clear affected entities or cancel the explosion: damage/knockback still apply.
    }
}
