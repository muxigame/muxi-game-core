package net.muxigame.core.feature.challenge;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Asymmetric research building, physical stairs, optional parkour and telegraphed spawn portals. */
public final class ChallengeArena {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("muxi_game_core:quarantine"));
    public static final int SIZE=81,BASE=64,FLOOR_HEIGHT=10,HEIGHT=31,SPACING=256;
    public static final List<String> ROOMS=ResearchLayout.FLOORS.stream().flatMap(List::stream).map(ResearchLayout.Room::name).toList();
    public record SpawnSite(String id,String name,int floor,BlockPos position,BlockPos lamp){}
    private final int slot;
    private final List<SpawnSite> sites;
    private int cursor;
    public ChallengeArena(int slot){
        if(slot<0||slot>=ChallengeRules.MAX_ROOMS)throw new IllegalArgumentException("arena slot");this.slot=slot;
        List<SpawnSite> points=new ArrayList<>();
        for(int f=0;f<3;f++){
            var small=ResearchLayout.rooms(f).stream().filter(r->r.width()>=8&&r.depth()>=8&&!r.kind().equals("lobby")).sorted(Comparator.comparingInt(r->r.width()*r.depth())).limit(4).toList();
            for(int i=0;i<small.size();i++){var r=small.get(i);points.add(new SpawnSite(f+"-room-"+i,"L"+(f+1)+" "+r.name(),f,pos(r.doorX()+r.inwardX()*3,1+f*10,r.doorZ()+r.inwardZ()*3),pos(r.doorX(),5+f*10,r.doorZ())));}
            points.add(new SpawnSite(f+"-north","L"+(f+1)+" 北端走廊",f,pos(40,1+f*10,5),pos(40,5+f*10,5)));
            points.add(new SpawnSite(f+"-south","L"+(f+1)+" 南端走廊",f,pos(40,1+f*10,76),pos(40,5+f*10,76)));
        }
        sites=List.copyOf(points);
    }
    public int origin(){return slot*SPACING;}
    public boolean ready(){return cursor>=SIZE*SIZE*HEIGHT;}
    public BlockPos pos(int x,int y,int z){return new BlockPos(origin()+x,BASE+y,z);}
    public BlockPos spawn(){return pos(40,1,40);}
    public boolean contains(double x,double y,double z){return x>=origin()+1&&x<origin()+80&&z>=1&&z<80&&y>=BASE+1&&y<BASE+30;}
    public int floor(double y){return Math.max(0,Math.min(2,(int)Math.floor((y-BASE-1)/10)));}
    public List<SpawnSite> sites(){return sites;}
    public List<BlockPos> spawns(int floor){return sites.stream().filter(s->s.floor==floor).map(SpawnSite::position).toList();}
    public BlockPos ammoStation(int floor){return pos(40,1+floor*10,36);}
    public BlockPos medicalStation(int floor){return pos(40,1+floor*10,44);}
    public BlockPos itemStation(int floor){return pos(40,1+floor*10,50);}
    public ResearchLayout.Room roomAt(double x,double y,double z){return ResearchLayout.at(floor(y),(int)Math.floor(x)-origin(),(int)Math.floor(z));}
    public BlockPos outside(ResearchLayout.Room room,int floor){return pos(room.doorX()-room.inwardX()*2,1+floor*10,room.doorZ()-room.inwardZ()*2);}
    public BlockPos recoveryWaypoint(double x,double y,double z,double targetY){return pos(40,1+floor(y)*10,40);}
    public void keepLoaded(ServerLevel level,boolean forced){for(int x=origin()>>4;x<=(origin()+SIZE-1)>>4;x++)for(int z=0;z<=(SIZE-1)>>4;z++)level.setChunkForced(x,z,forced);}
    public void lamps(ServerLevel level,List<SpawnSite> active,boolean on){
        for(var s:sites){BlockState wanted=Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,on&&active.contains(s));if(!level.getBlockState(s.lamp).equals(wanted))level.setBlock(s.lamp,wanted,2);}
    }
    public BlockState block(int x,int y,int z){
        int f=Math.min(2,y/10),h=y%10;var room=ResearchLayout.at(f,x,z);
        if(y==30)return (x%8==0||z%8==0?Blocks.SEA_LANTERN:Blocks.SMOOTH_QUARTZ).defaultBlockState();
        if(x==0||x==80||z==0||z==80)return (h>=3&&h<=6?Blocks.TINTED_GLASS:Blocks.WHITE_CONCRETE).defaultBlockState();
        if((x==1||x==79||z==1||z==79)&&h>0)return Blocks.GRAY_CONCRETE.defaultBlockState();
        if(h==5)for(var s:sites)if(s.floor==f){int dx=Math.abs(s.lamp.getX()-origin()-x),dz=Math.abs(s.lamp.getZ()-z);if(dx+dz==0)return Blocks.REDSTONE_LAMP.defaultBlockState();if(dx+dz==1)return Blocks.RED_STAINED_GLASS.defaultBlockState();}
        boolean north=z>=12&&z<=21,south=z>=59&&z<=68;
        if(x>=38&&x<=42&&(north||south)&&y>0){int step=north?z-12:68-z;if(y==1+step||y==11+step)return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,north?Direction.SOUTH:Direction.NORTH);return Blocks.AIR.defaultBlockState();}
        if((x==37||x==43)&&(north||south)&&h>=1&&h<=2)return Blocks.IRON_BARS.defaultBlockState();
        if(y==21&&x>=38&&x<=42&&(z==11||z==69))return Blocks.IRON_BARS.defaultBlockState();
        if(h==0){
            if(room==null){if(x==40&&z%4==0)return Blocks.SEA_LANTERN.defaultBlockState();return (x==39||x==41?f==0?Blocks.YELLOW_CONCRETE:f==1?Blocks.CYAN_CONCRETE:Blocks.RED_CONCRETE:Blocks.POLISHED_ANDESITE).defaultBlockState();}
            return (room.wall(x,z)?Blocks.GRAY_CONCRETE:x%8==0||z%8==0?Blocks.WHITE_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
        }
        if(x>=39&&x<=41&&z==36&&h<=2)return (x==40&&h==1?Blocks.BARREL:h==2?Blocks.YELLOW_CONCRETE:Blocks.IRON_BLOCK).defaultBlockState();
        if(x==40&&z==44&&h<=2)return (h==1?Blocks.EMERALD_BLOCK:Blocks.SEA_LANTERN).defaultBlockState();
        if(x==40&&z==50&&h<=2)return (h==1?Blocks.REDSTONE_BLOCK:Blocks.IRON_TRAPDOOR).defaultBlockState();
        if(room!=null&&room.wall(x,z)){
            if(room.door(x,z)&&h<=3)return Blocks.AIR.defaultBlockState();
            return (h>=3&&h<=5?room.kind().equals("containment")?Blocks.LIME_STAINED_GLASS:Blocks.LIGHT_BLUE_STAINED_GLASS:h==8?Blocks.CYAN_CONCRETE:Blocks.SMOOTH_QUARTZ).defaultBlockState();
        }
        if(h==9&&(x%8==4&&z%8==4))return Blocks.SEA_LANTERN.defaultBlockState();
        if(room==null||room.access(x,z))return Blocks.AIR.defaultBlockState();
        int lx=x-room.x1(),lz=z-room.z1(),w=room.width(),d=room.depth();
        switch(room.kind()){
            case "office","lobby" -> {
                if(lz==2&&lx>=2&&lx<w-2){if(h==1)return Blocks.SMOOTH_QUARTZ.defaultBlockState();if(h==2&&lx%4==0)return Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState();}
                if(lz==4&&lx%4==0&&h==1)return Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH);
                if(lx==2&&lz>=7&&lz<d-2&&h<=3)return Blocks.BOOKSHELF.defaultBlockState();
            }
            case "security","utilities" -> {
                if(lx==w-3&&lz>=2&&lz<d-2&&h<=3)return (lz%3==0?Blocks.SEA_LANTERN:Blocks.IRON_BLOCK).defaultBlockState();
                if(lx==2&&lz==2&&h==1)return Blocks.CAULDRON.defaultBlockState();
            }
            case "lab" -> {
                if(lx>=3&&lx<w-3&&lz%7==3){if(h==1)return Blocks.SMOOTH_QUARTZ.defaultBlockState();if(h==2&&lx%4==0)return Blocks.BREWING_STAND.defaultBlockState();}
                if(lx==w-3&&lz>=3&&lz<d-3&&h<=3)return (lz%3==0?Blocks.SEA_LANTERN:Blocks.WHITE_CONCRETE).defaultBlockState();
            }
            case "ward" -> {
                if(lx>=2&&lx<w-2&&lx%4==2&&lz>=2&&lz<d-2&&(lz%6==2||lz%6==3)&&h==1)return Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,lz%6==3?net.minecraft.world.level.block.state.properties.BedPart.HEAD:net.minecraft.world.level.block.state.properties.BedPart.FOOT);
                if(lx==w-2&&Math.abs(lz-d/2)<=2&&h<=5)return (h==3||lz==d/2?Blocks.RED_CONCRETE:Blocks.WHITE_CONCRETE).defaultBlockState();
            }
            case "store","cold" -> {
                if(lx>1&&lx<w-2&&lz>1&&lz<d-2&&(lx%6==2||lx%6==3)&&(lz%6==2||lz%6==3)&&h<=3)return (room.kind().equals("cold")?h==2?Blocks.PACKED_ICE:Blocks.WHITE_CONCRETE:Blocks.BARREL).defaultBlockState();
            }
            case "server" -> {
                if(lx>=2&&lx<w-2&&lz>=2&&lz<d-2&&(lx%6==2||lx%6==3)&&lz%7<4&&h<=4)return (h==3?Blocks.SEA_LANTERN:Blocks.BLACK_CONCRETE).defaultBlockState();
            }
            case "containment" -> {
                if(lx>=w-7&&lx<=w-3&&lz>=d-7&&lz<=d-3&&h<=4){if(lx==w-7||lx==w-3||lz==d-7||lz==d-3)return Blocks.LIME_STAINED_GLASS.defaultBlockState();if(h==1&&lx==w-5&&lz==d-5)return Blocks.AMETHYST_BLOCK.defaultBlockState();}
            }
            case "maintenance" -> {
                for(int i=0;i<Math.min(5,(w-6)/4);i++)if(lx>=3+i*4&&lx<=4+i*4&&lz>=d/2&&lz<=d/2+1&&h<=1+i/2)return Blocks.POLISHED_ANDESITE.defaultBlockState();
                if(lx>=3&&lx<w-3&&lz>=d-5&&lz<=d-4&&h==4)return Blocks.IRON_BLOCK.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }
    public void build(ServerLevel level,int budget){for(int i=0;i<budget&&!ready();i++,cursor++){int x=cursor%SIZE,z=(cursor/SIZE)%SIZE,y=cursor/(SIZE*SIZE);var p=pos(x,y,z);var state=block(x,y,z);if(!level.getBlockState(p).equals(state))level.setBlock(p,state,18);}}
    public void labels(ServerLevel level){
        for(int f=0;f<3;f++){
            for(var room:ResearchLayout.rooms(f))label(level,pos(room.doorX(),4+f*10,room.doorZ()),"L"+(f+1)+" "+room.name());
            label(level,pos(40,4+f*10,36),"弹药补给柜 · 附近按换弹键 R");label(level,pos(40,4+f*10,44),"医疗补给 · 右键");label(level,pos(40,4+f*10,50),"道具补给 · 右键");
            label(level,pos(40,4+f*10,10),"北侧楼梯");label(level,pos(40,4+f*10,70),"南侧楼梯");
        }
        for(var s:sites)label(level,s.lamp.above(2),"刷怪口 · "+s.name);
    }
    private void label(ServerLevel level,BlockPos p,String text){var display=net.minecraft.world.entity.EntityType.TEXT_DISPLAY.create(level);if(display!=null){display.setPos(p.getX()+0.5,p.getY(),p.getZ()+0.5);var data=display.saveWithoutId(new net.minecraft.nbt.CompoundTag());var name=net.minecraft.network.chat.Component.literal(text);if(text.startsWith("刷怪口"))name.withStyle(net.minecraft.ChatFormatting.RED);data.putString("text",net.minecraft.network.chat.Component.Serializer.toJson(name,level.registryAccess()));data.putString("billboard","center");display.load(data);level.addFreshEntity(display);}}
}
