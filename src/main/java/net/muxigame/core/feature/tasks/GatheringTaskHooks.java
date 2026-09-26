package net.muxigame.core.feature.tasks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.muxigame.core.feature.tasks.integration.RightClickHarvestTaskHooks;
import java.util.*;
import java.util.function.IntSupplier;

/** Harvest actual mature plants and picked flowers, never generic item pickups or inventory polling. */
public final class GatheringTaskHooks {
    private static final TagKey<Block> EXTRA_CROPS=TagKey.create(Registries.BLOCK,ResourceLocation.parse("muxi_game_core:task_crops"));
    private static final TagKey<Block> EXTRA_FLOWERS=TagKey.create(Registries.BLOCK,ResourceLocation.parse("muxi_game_core:task_flowers"));
    private final DailyTasksFeature feature;
    private final List<Runnable> pending=new ArrayList<>();
    private final Set<String> blocks=new HashSet<>();
    public GatheringTaskHooks(DailyTasksFeature feature,IEventBus bus) {
        this.feature=feature;
        bus.addListener(EventPriority.LOWEST,true,this::drops);
        if(ModList.get().isLoaded("rightclickharvest")) RightClickHarvestTaskHooks.register(this,bus);
    }
    public static boolean crop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.is(BlockTags.CROPS) || state.is(EXTRA_CROPS)
            || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock
            || state.getBlock() instanceof SweetBerryBushBlock;
    }
    public static boolean mature(BlockState state) {
        if(!crop(state)) return false;
        if(state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        for(var p:state.getProperties()) if(p instanceof IntegerProperty age && (p.getName().equals("age") || p.getName().startsWith("age_")))
            return state.getValue(age).equals(Collections.max(age.getPossibleValues()));
        // No age means a harvestable fruit block (melon/pumpkin), or a tagged crop with no age property.
        return true;
    }
    private void drops(BlockDropsEvent event) {
        var player=TaskOwnership.credit(event.getBreaker());
        if(player==null || player.isCreative() || player.isSpectator() || pending.size()>=4096) return;
        BlockState state=event.getState(); boolean flower=state.is(BlockTags.FLOWERS) || state.is(EXTRA_FLOWERS);
        if(!flower && !mature(state)) return;
        String key=event.getLevel().dimension().location()+":"+event.getPos()+":"+player.getUUID();
        if(!blocks.add(key)) return;
        IntSupplier amount=()->{
            if(event.isCanceled() || !event.getLevel().hasChunkAt(event.getPos())) return 0;
            // A denied break or a tool merely calculating prospective drops must not count.
            if(event.getLevel().getBlockState(event.getPos())==state) return 0;
            if(!flower) return event.getDrops().stream().anyMatch(e->!e.getItem().isEmpty())?1:0;
            return event.getDrops().stream().filter(e->e.getItem().is(ItemTags.FLOWERS)
                || e.getItem().is(state.getBlock().asItem())).mapToInt(e->e.getItem().getCount()).sum();
        };
        pending.add(feature.gatheringCredit(player,flower?TaskCatalog.Kind.FLOWERS:TaskCatalog.Kind.HARVESTED,
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),amount));
    }
    /** RightClickHarvest only emits this after a successful mature harvest, including its modded-crop support. */
    public void rightClickHarvest(net.minecraft.world.entity.player.Player actor,Block block) {
        if(actor.level().isClientSide() || actor.isCreative() || actor.isSpectator() || !crop(block.defaultBlockState())) return;
        var player=TaskOwnership.credit(actor);
        if(player!=null) feature.gatheringCredit(player,TaskCatalog.Kind.HARVESTED,BuiltInRegistries.BLOCK.getKey(block).toString(),1).run();
    }
    public void flush() {
        List<Runnable> work=new ArrayList<>(pending); clear(); work.forEach(Runnable::run);
    }
    public void clear() { pending.clear(); blocks.clear(); }
}
