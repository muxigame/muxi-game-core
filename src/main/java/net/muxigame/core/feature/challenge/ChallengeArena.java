package net.muxigame.core.feature.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Three floors, four rooms per floor, central cross corridors. Bounded incremental generation. */
public final class ChallengeArena {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("muxi_game_core:quarantine"));
    public static final int SIZE=49, BASE=64, HEIGHT=25, SPACING=256;
    public static final List<String> ROOMS=List.of("接待大厅","检疫室","安保室","物资仓", "实验室 A","实验室 B","医疗站","电源室", "指挥室","服务器机房","隔离舱","屋顶防线");
    private final int slot;
    private int cursor;
    public ChallengeArena(int slot) { if(slot<0 || slot>=ChallengeRules.MAX_ROOMS) throw new IllegalArgumentException("arena slot");this.slot=slot; }
    public int origin() { return slot*SPACING; }
    public boolean ready() { return cursor>=SIZE*SIZE*HEIGHT; }
    public BlockPos spawn() { return pos(24,1,24); }
    public BlockPos pos(int x,int y,int z) { return new BlockPos(origin()+x,BASE+y,z); }
    public boolean contains(double x,double y,double z) { return x>=origin()+1 && x<origin()+48 && z>=1 && z<48 && y>=BASE+1 && y<BASE+24; }
    public int floor(double y) { return Math.max(0,Math.min(2,((int)y-BASE)/8)); }
    public List<BlockPos> spawns(int floor) {
        int y=1+8*floor;
        return List.of(pos(5,y,5),pos(43,y,5),pos(5,y,43),pos(43,y,43),pos(12,y,12),pos(36,y,36));
    }
    public boolean atPad(BlockPos p,int x,int z) { return p.getX()==origin()+x && p.getZ()==z && (p.getY()-BASE)%8==0 && p.getY()>=BASE && p.getY()<BASE+24; }
    public BlockPos lift(int floor,boolean up) { return pos(up?26:22,1+8*((floor+(up?1:2))%3),24); }
    public BlockState block(int x,int y,int z) {
        int floor=y/8;
        if(y==HEIGHT-1) return Blocks.BEDROCK.defaultBlockState();
        if(y%8==0) {
            if(x==22 && z==24) return Blocks.LAPIS_BLOCK.defaultBlockState();
            if(x==26 && z==24) return Blocks.PURPUR_BLOCK.defaultBlockState();
            if(x==24 && z==20) return Blocks.GOLD_BLOCK.defaultBlockState();
            if(x==24 && z==28) return Blocks.EMERALD_BLOCK.defaultBlockState();
            if(x==12 && z==24) return Blocks.REDSTONE_BLOCK.defaultBlockState();
            if(x==24 && z==24) return Blocks.SEA_LANTERN.defaultBlockState();
            if(x%8==4 && z%8==4) return Blocks.SEA_LANTERN.defaultBlockState();
            return (floor==0?Blocks.POLISHED_ANDESITE:floor==1?Blocks.DEEPSLATE_TILES:Blocks.POLISHED_DIORITE).defaultBlockState();
        }
        if(x==0 || x==48 || z==0 || z==48) return (y%8>=3 && y%8<=5?Blocks.TINTED_GLASS:Blocks.BEDROCK).defaultBlockState();
        // Rooms on either side of a seven-block-wide cross corridor. Two-wide doors.
        boolean wallX=(x==20 || x==28) && (z<20 || z>28) && !(z>=10&&z<=12 || z>=36&&z<=38);
        boolean wallZ=(z==20 || z==28) && (x<20 || x>28) && !(x>=10&&x<=12 || x>=36&&x<=38);
        if((wallX || wallZ) && y%8<=6) return (y%8>=3?Blocks.GLASS:Blocks.STONE_BRICKS).defaultBlockState();
        int rx=Math.min(x,48-x),rz=Math.min(z,48-z);
        // Perimeter shelves, counters and waist-high cover leave the doorways and spawn cells open.
        if(rx==3 && rz>=8 && rz<=16 && y%8<=3)
            return (floor==0?Blocks.BARREL:floor==1?Blocks.WHITE_CONCRETE:Blocks.BOOKSHELF).defaultBlockState();
        if(rx>=8 && rx<=12 && rz==16 && y%8==1) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        if(rx==10 && rz==16 && y%8==2) return (floor==1?Blocks.BREWING_STAND:Blocks.IRON_BARS).defaultBlockState();
        if(rx==16 && rz>=8 && rz<=10 && y%8==1) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        if(rx==10 && rz==10 && y%8==7) return Blocks.SEA_LANTERN.defaultBlockState();
        if(y%8==1 && (x==8 || x==40) && (z==8 || z==40)) return Blocks.IRON_BLOCK.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }
    public void build(ServerLevel level,int budget) {
        for(int i=0;i<budget && !ready();i++,cursor++) {
            int x=cursor%SIZE,z=(cursor/SIZE)%SIZE,y=cursor/(SIZE*SIZE);
            BlockPos p=pos(x,y,z); BlockState state=block(x,y,z);
            if(!level.getBlockState(p).equals(state)) level.setBlock(p,state,2);
        }
    }
    public void labels(ServerLevel level) {
        for(int floor=0;floor<3;floor++) {
            int y=3+floor*8;
            for(int i=0;i<4;i++) label(level,pos(i%2==0?10:38,y,i<2?10:38),"第 "+(floor+1)+" 层 · "+ROOMS.get(floor*4+i));
            label(level,pos(24,y,20),"弹药补充 · 右键金块");
            label(level,pos(24,y,28),"医疗 / 食物 · 右键绿宝石");
            label(level,pos(12,y,24),"道具刷新 · 右键红石块");
            label(level,pos(22,y,24),"下层 ↓");label(level,pos(26,y,24),"上层 ↑");
        }
    }
    private void label(ServerLevel level,BlockPos p,String text) {
        var display=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(level);
        if(display!=null){
            display.setPos(p.getX()+0.5,p.getY(),p.getZ()+0.5);
            var data=display.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            data.putString("text",net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.literal(text),level.registryAccess()));
            data.putString("billboard","center");display.load(data);level.addFreshEntity(display);
        }
    }
}
