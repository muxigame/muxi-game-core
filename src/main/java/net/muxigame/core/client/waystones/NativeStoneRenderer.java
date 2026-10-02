package net.muxigame.core.client.waystones;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.util.FormattedCharSequence;
import xaero.map.WorldMapSession;
import xaero.map.mods.gui.Waypoint;
import java.util.*;

/** Draws into the installed Xaero renderer. No second map, portal node or minimap preference exists. */
public final class NativeStoneRenderer {
    private NativeStoneRenderer() {}
    public static ResourceKey<Level> viewDimension() {
        var session=WorldMapSession.getCurrentSession();
        if(session!=null && session.isUsable() && session.getMapProcessor().getMapWorld()!=null) {
            var dimension=session.getMapProcessor().getMapWorld().getCustomDimensionId();
            if(dimension!=null)return dimension;
        }
        return Minecraft.getInstance().level==null?null:Minecraft.getInstance().level.dimension();
    }
    public static void draw(Waypoint waypoint,boolean focused,float scale,double x,double y,GuiGraphics graphics) {
        var mc=Minecraft.getInstance();
        boolean linked=WaystoneMapClient.portalLinked(viewDimension(),new BlockPos(waypoint.getX(),waypoint.getY(),waypoint.getZ()));
        List<FormattedCharSequence> lines=new ArrayList<>(mc.font.split(Component.literal(waypoint.getName()),180));
        if(focused && linked)lines.addAll(mc.font.split(Component.translatable("muxi.map.waystone.portal_connected"),180));
        int width=lines.stream().mapToInt(mc.font::width).max().orElse(0),height=lines.size()*mc.font.lineHeight;
        // Xaero's original renderer scales its Minecraft font by three in this coordinate system.
        float s=Math.max(0.1f,scale*3);
        var label=StoneLabelLayout.place(x,y,width*s,height*s,focused);
        var pose=graphics.pose();pose.pushPose();pose.translate(x,y,0);pose.scale(s,s,1);
        try {
            // Deliberate stone silhouette rather than Xaero's single-letter symbol.
            graphics.fill(-6,4,7,7,0xff343b45);graphics.fill(-4,-9,5,4,0xffa6b0ba);
            graphics.fill(-3,-11,4,-9,0xffc5cdd5);graphics.fill(-4,-8,-2,3,0xff737f8b);
            graphics.fill(-1,-6,3,-5,0xffedf5f8);graphics.fill(-1,-3,3,-2,0xffedf5f8);
            if(linked) {
                graphics.fill(3,-12,10,-3,0xff47394e);graphics.fill(4,-11,9,-4,0xffaa5bdd);
                graphics.fill(5,-10,8,-5,0xff58268c);
            }
            if(label!=null) {
                int lx=(int)((label.x()-x)/s),ly=(int)((label.y()-y)/s);
                graphics.fill(lx-3,ly-2,lx+width+3,ly+height+2,focused?0xee152033:0xc9152033);
                for(int i=0;i<lines.size();i++)graphics.drawString(mc.font,lines.get(i),lx,ly+i*mc.font.lineHeight,0xffffffff,true);
            }
        } finally {pose.popPose();}
    }
}
