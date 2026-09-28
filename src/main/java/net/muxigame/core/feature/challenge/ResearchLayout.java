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
            r("隔离检疫大厅","containment",44,35,60,51,Side.W,42),r("冷藏样本库","cold",64,42,78,54,Side.W,47),
            r("洗消作业间","utilities",48,57,60,69,Side.E,62),r("设备货仓","store",64,58,78,77,Side.W,65),
            r("卫生间","utilities",48,73,60,78,Side.N,53)
        ),
        List.of(
            r("生化实验室 A","lab",2,2,18,20,Side.E,13),r("实验准备室 A","utilities",22,2,32,11,Side.S,25),
            r("消毒准备间","utilities",22,15,32,24,Side.E,18),r("分析实验室","lab",2,25,18,43,Side.E,32),
            r("样本登记室","office",22,28,36,40,Side.E,33),r("药剂储藏库","cold",2,48,12,63,Side.E,54),
            r("生化实验室 B","lab",16,45,32,57,Side.E,50),r("临床试验室 C","lab",16,61,32,77,Side.E,67),
            r("清洁准备间","utilities",2,67,12,78,Side.E,71),
            r("能源控制室","utilities",48,2,61,18,Side.E,10),r("低温冷冻库","cold",65,2,78,18,Side.W,10),
            r("组织培养室","lab",48,23,61,37,Side.W,29),r("显微分析室","lab",65,23,78,37,Side.W,29),
            r("医疗救护站","ward",44,42,61,57,Side.W,47),r("观察病房","ward",65,42,78,58,Side.W,49),
            r("手术准备室","ward",48,62,61,78,Side.E,68),r("废物暂存间","utilities",65,63,78,78,Side.W,69)
        ),
        List.of(
            r("数据备份室","server",2,2,12,17,Side.E,10),r("服务器机房 A","server",16,2,32,17,Side.E,10),
            r("高性能计算中心","server",2,21,32,36,Side.E,27),r("通风维护作业厅","maintenance",2,42,32,65,Side.E,50),
            r("备件库","store",2,69,14,78,Side.N,7),r("机房值班室","office",18,69,32,78,Side.N,24),
            r("中央指挥室","office",48,2,62,19,Side.E,11),r("通信设备室","server",66,2,78,19,Side.W,11),
            r("监控值班室","office",48,24,62,36,Side.W,28),r("应急办公室","office",66,24,78,36,Side.W,28),
            r("高危隔离大厅","containment",44,41,62,61,Side.W,48),r("独立隔离室 A","containment",66,41,78,51,Side.W,45),
            r("独立隔离室 B","containment",66,55,78,65,Side.W,59),r("处置准备间","utilities",48,65,62,78,Side.E,70),
            r("缓冲更衣间","utilities",66,69,78,78,Side.W,72)
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
