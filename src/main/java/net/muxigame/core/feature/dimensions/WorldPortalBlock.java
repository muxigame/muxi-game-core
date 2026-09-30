package net.muxigame.core.feature.dimensions;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;

/** Vanilla portal animation and collision, with our explicit frame and destination. */
public final class WorldPortalBlock extends NetherPortalBlock {
    private final int destination;
    public WorldPortalBlock(int destination) {
        super(Properties.ofFullCopy(Blocks.NETHER_PORTAL).noLootTable());
        this.destination=destination;
    }
    public int destination() { return destination; }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity) {
        if(!entity.canUsePortal(false))return;
        if(entity.isOnPortalCooldown())entity.setPortalCooldown(40);
        else entity.setAsInsidePortal(this,pos);
    }
    @Override public DimensionTransition getPortalDestination(ServerLevel level,Entity entity,BlockPos pos) {
        return WorldPortals.transition(level,entity,pos,this);
    }
    @Override public int getPortalTransitionTime(ServerLevel level,Entity entity) { return 40; }
    @Override protected BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos neighborPos) {
        return WorldPortals.find(level,pos,destination,true)==null?Blocks.AIR.defaultBlockState():state;
    }
    @Override protected void randomTick(BlockState state,ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random) {
        // These gates are transport only; do not inherit the Nether portal's piglin spawn rule.
    }
}
