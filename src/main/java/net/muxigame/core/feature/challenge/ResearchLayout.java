package net.muxigame.core.feature.challenge;

import java.util.*;

/** Architectural floor plans: non-overlapping rooms of varied sizes, joined by circulation space. */
public final class ResearchLayout {
    private ResearchLayout(){}
    public enum Side { N,S,E,W }
    public record Room(String name,String kind,int x1,int z1,int x2,int z2,Side doorSide,int doorAt) {
        public int width(){return x2-x1+1;}
        public int depth(){return z2-z1+1;}
        public boolean contains(int x,int z){return x>=x1&&x<=x2&&z>=z1&&z<=z2;}
        public boolean interior(int x,int z){return x>x1&&x<x2&&z>z1&&z<z2;}
        public boolean wall(int x,int z){return x==x1||x==x2||z==z1||z==z2;}
        public int doorX(){return switch(doorSide){case E->x2;case W->x1;default->doorAt+1;};}
        public int doorZ(){return switch(doorSide){case N->z1;case S->z2;default->doorAt+1;};}
        public int inwardX(){return doorSide==Side.E?-1:doorSide==Side.W?1:0;}
        public int inwardZ(){return doorSide==Side.S?-1:doorSide==Side.N?1:0;}
        public boolean door(int x,int z){return switch(doorSide){case N->z==z1&&x>=doorAt&&x<doorAt+3;case S->z==z2&&x>=doorAt&&x<doorAt+3;case E->x==x2&&z>=doorAt&&z<doorAt+3;case W->x==x1&&z>=doorAt&&z<doorAt+3;};}
        public boolean access(int x,int z){return (doorSide==Side.N||doorSide==Side.S)?Math.abs(x-doorX())<=2&&Math.abs(z-doorZ())<=6:Math.abs(z-doorZ())<=2&&Math.abs(x-doorX())<=6;}
    }
    private static Room r(String name,String kind,int x1,int z1,int x2,int z2,Side side,int at){return new Room(name,kind,x1,z1,x2,z2,side,at);}
    public static final List<List<Room>> FLOORS=List.of(
        List.of(
            r("行政接待办公室","office",2,2,14,12,Side.S,7),r("门禁安保室","security",18,2,32,12,Side.S,24),
            r("档案资料室","office",2,17,14,29,Side.E,21),r("洗消更衣室","utilities",18,17,32,24,Side.N,24),
            r("新员培训教室","office",2,34,20,54,Side.E,41),r("样本接收登记","lab",24,28,36,40,Side.E,33),
            r("器械准备间","store",24,44,36,54,Side.E,48),r("员工休息室","office",2,59,12,76,Side.E,65),
            r("后勤工具库","store",16,59,32,70,Side.E,64),r("清洁间","utilities",16,74,32,78,Side.N,23),
            r("入口安检大厅","lobby",34,74,46,79,Side.N,39),
            r("配电间","utilities",48,2,60,14,Side.E,8),r("值班办公室","office",64,2,78,14,Side.W,8),
            r("访客接待室","office",48,19,60,30,Side.N,53),r("物资收发大厅","store",64,19,78,38,Side.W,27),
            r("零号封锁门 · 核心感染巢穴","nest",44,35,60,51,Side.W,42),r("冷藏样本库","cold",64,42,78,54,Side.W,47),
            r("洗消作业间","utilities",48,57,60,69,Side.E,62),r("设备货仓","store",64,58,78,77,Side.W,65),
            r("卫生间","utilities",48,73,60,78,Side.N,53)
        ),
        List.of(
            r("西北准备间","utilities",2,2,16,17,Side.S,6),r("标本档案室","office",20,2,32,17,Side.S,25),
            r("东侧能源站","utilities",48,2,62,17,Side.S,53),r("低温样本库","cold",66,2,78,17,Side.S,70),
            r("西侧分析室","lab",2,26,16,48,Side.E,35),r("中央生化实验大厅","lab",22,28,60,49,Side.N,38),
            r("组织培养翼","lab",22,54,60,76,Side.W,64),r("临床观察室","ward",66,28,78,44,Side.W,34),
            r("手术准备间","ward",66,49,78,62,Side.W,54),r("废物隔离间","utilities",66,67,78,78,Side.W,71),
            r("西侧清洁库","store",2,73,16,78,Side.N,6),r("西侧破损观察室","office",2,52,16,56,Side.E,53)
        ),
        List.of(
            r("中央计算机房","server",18,28,48,49,Side.W,36),r("高危隔离翼","containment",52,28,78,49,Side.N,63),
            r("南侧维护作业厅","maintenance",22,54,60,76,Side.N,37),r("应急通信室","server",66,54,78,66,Side.W,58),
            r("处置缓冲间","utilities",66,70,78,78,Side.N,71),r("西侧设备档案库","store",2,2,16,16,Side.S,6),
            r("北侧指挥大厅","office",22,2,48,16,Side.S,32),r("独立隔离室 A","containment",52,2,64,16,Side.S,57),
            r("独立隔离室 B","containment",68,2,78,16,Side.S,71),r("西侧检修小室","utilities",2,24,12,42,Side.E,32),
            r("西侧破损机房","server",2,46,12,52,Side.S,6),r("备件装卸室","store",2,73,16,78,Side.N,6)
        )
    );
    public static List<Room> rooms(int floor){return FLOORS.get(Math.max(0,Math.min(2,floor)));}
    private static final Room[][][] CELLS=new Room[3][81][81];
    public static Room at(int floor,int x,int z){return x<0||x>80||z<0||z>80?null:CELLS[Math.max(0,Math.min(2,floor))][x][z];}
    static {
        for(int f=0;f<3;f++)for(var room:rooms(f))for(int x=room.x1;x<=room.x2;x++)for(int z=room.z1;z<=room.z2;z++)CELLS[f][x][z]=room;
        for(var rooms:FLOORS)for(var room:rooms){
            if(room.x1<2||room.z1<2||room.x2>78&&!(room.kind.equals("lobby"))||room.z2>79||room.width()<5||room.depth()<5)throw new IllegalStateException("Invalid room: "+room.name);
            int min=room.doorSide==Side.N||room.doorSide==Side.S?room.x1:room.z1;
            int max=room.doorSide==Side.N||room.doorSide==Side.S?room.x2:room.z2;
            if(room.doorAt<=min||room.doorAt+2>=max)throw new IllegalStateException("Invalid doorway: "+room.name);
            for(var other:rooms)if(room!=other&&Math.max(room.x1,other.x1)<Math.min(room.x2,other.x2)&&Math.max(room.z1,other.z1)<Math.min(room.z2,other.z2))throw new IllegalStateException("Overlapping rooms");
        }
    }
}
