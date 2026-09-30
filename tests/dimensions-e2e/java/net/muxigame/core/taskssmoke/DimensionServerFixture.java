package net.muxigame.core.taskssmoke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.muxigame.core.feature.dimensions.WorldDimensions;
import net.muxigame.core.feature.dimensions.WorldPortals;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.nio.file.*;

/** Test-only server fixture. All mutations occur in the disposable loopback world. */
@Mod("muxi_dimensions_e2e")
public final class DimensionServerFixture {
    public DimensionServerFixture() { NeoForge.EVENT_BUS.addListener(this::start); NeoForge.EVENT_BUS.addListener(this::login); }
    private void start(ServerStartedEvent event) {
        var server=event.getServer();
        server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        server.overworld().setDayTime(6000);
        BlockPos feet=new BlockPos(0,180,0);
        for(var destination:WorldDimensions.ALL) {
            var level=server.getLevel(destination.key());
            if(level==null) throw new IllegalStateException("Missing dimension "+destination.key());
            for(int x=-5;x<=24;x++)for(int z=-5;z<=5;z++) {
                level.setBlockAndUpdate(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                for(int y=0;y<=3;y++)level.setBlockAndUpdate(feet.offset(x,y,z),Blocks.AIR.defaultBlockState());
            }
            level.setDefaultSpawnPos(feet,0);
        }
        gate(server.overworld(),2,Blocks.GRASS_BLOCK);
        gate(server.getLevel(WorldDimensions.OVERWORLD),14,Blocks.QUARTZ_BLOCK);
        try { Files.writeString(Path.of("e2e-ready"),"ready"); }
        catch(Exception error) { throw new RuntimeException(error); }
    }
    private void gate(net.minecraft.server.level.ServerLevel level,int x,net.minecraft.world.level.block.Block material) {
        for(int w=0;w<4;w++)for(int h=0;h<5;h++)
            level.setBlock(new BlockPos(x+w,179+h,0),(w==0||w==3||h==0||h==4?material:Blocks.AIR).defaultBlockState(),18);
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || !player.getGameProfile().getName().equals("MuxiTaskPreview")) return;
        if(!player.getPersistentData().getBoolean("dimension_qa_initialized")) {
            player.teleportTo(player.server.overworld(),0.5,180,0.5,0,0);
            player.getInventory().setItem(0,new ItemStack(Items.DIAMOND,23));
            player.getInventory().setItem(1,new ItemStack(Items.FLINT_AND_STEEL));
            player.giveExperienceLevels(7);
            player.getPersistentData().putBoolean("dimension_qa_initialized",true);
        }
    }
}
