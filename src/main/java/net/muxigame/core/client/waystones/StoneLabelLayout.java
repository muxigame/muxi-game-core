package net.muxigame.core.client.waystones;

import java.util.*;

/** Per-frame screen-space label placement; crowded stones remain selectable with full names on hover. */
public final class StoneLabelLayout {
    private StoneLabelLayout() {}
    public record Rect(double x,double y,double width,double height) {
        public boolean intersects(Rect b){return x<b.x+b.width+3 && x+width+3>b.x && y<b.y+b.height+3 && y+height+3>b.y;}
    }
    private static final List<Rect> labels=new ArrayList<>();
    public static void beginFrame(){labels.clear();}
    public static Rect place(double x,double y,double width,double height,boolean focus) {
        for(int i=0;i<8;i++) {
            double dx=(i%2==0)?12:-width-12,dy=(i/2)*(height+5)-height/2;
            Rect rect=new Rect(x+dx,y+dy,width,height);
            if(labels.stream().noneMatch(rect::intersects)){labels.add(rect);return rect;}
        }
        if(focus){Rect rect=new Rect(x+12,y-height/2,width,height);labels.add(rect);return rect;}
        return null;
    }
}
