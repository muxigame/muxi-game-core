package net.muxigame.core.client.tasks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.muxigame.core.feature.tasks.TaskNetwork;
import java.util.*;

/** Mouse-unlocked view of the same left-side tasks. Native item renderer and tooltip, no fake item textures. */
public final class DailyTaskScreen extends Screen {
    private static final int ROW=48;
    private int left,top,column,first,visible;
    private Tab tab=Tab.DAILY;
    private String signature="";
    private final List<Button> claimButtons=new ArrayList<>();
    private final List<Button> rerollButtons=new ArrayList<>();
    private enum Tab { DAILY, MAINLINE }
    public DailyTaskScreen() { super(Component.translatable("muxi.tasks.title")); }
    @Override protected void init() {
        left=12; column=Math.min(270,width-24); top=52;
        visible=Math.max(1,(height-84)/ROW);
        int size=DailyTasksClient.empty()?0:DailyTasksClient.snapshot.rows().size();
        first=Math.max(0,Math.min(first,Math.max(0,size-visible)));
        rebuild(); DailyTasksClient.request();
    }
    private String signature() {
        var s=DailyTasksClient.snapshot;
        return s==null?"":s.day()+":"+s.rerollsRemaining()+":"+s.rows().stream().map(r->r.id()+r.claimed()).toList()+":"+first;
    }
    private void rebuild() {
        clearWidgets(); claimButtons.clear(); rerollButtons.clear(); signature=signature();
        TabButton daily=new TabButton(left,18,76,18,Component.translatable("muxi.tasks.tab_daily"),b->{tab=Tab.DAILY; rebuild();},tab==Tab.DAILY);
        TabButton mainline=new TabButton(left+82,18,76,18,Component.translatable("muxi.tasks.tab_mainline"),b->{tab=Tab.MAINLINE; rebuild();},tab==Tab.MAINLINE);
        addRenderableWidget(daily); addRenderableWidget(mainline);
        if(tab==Tab.MAINLINE) {
            addRenderableWidget(new TextButton(left+column-40,height-27,40,18,Component.translatable("gui.done"),b->onClose()));
            return;
        }
        var s=DailyTasksClient.snapshot;
        if(s!=null) for(int i=first;i<Math.min(s.rows().size(),first+visible);i++) {
            TaskNetwork.Row row=s.rows().get(i); int y=top+33+(i-first)*ROW;
            Button button=new TextButton(left+column-44,y+26,44,18,
                Component.translatable(row.claimed()?"muxi.tasks.claimed":"muxi.tasks.claim"),b->DailyTasksClient.claim(current(row.id())));
            button.active=row.ready(); claimButtons.add(button); addRenderableWidget(button);
            Button reroll=new TextButton(left+column-92,y+26,46,18,Component.translatable("muxi.tasks.reroll"),b->confirmReroll(current(row.id())));
            reroll.active=DailyTasksClient.rerollable(row); rerollButtons.add(reroll); addRenderableWidget(reroll);
        }
        addRenderableWidget(new TextButton(left,height-27,110,18,Component.translatable(DailyTasksClient.settings.visible?"muxi.tasks.hide":"muxi.tasks.show"),b->{
            DailyTasksClient.settings.visible=!DailyTasksClient.settings.visible; DailyTasksClient.settings.save(); rebuild();
        }));
        addRenderableWidget(new TextButton(left+column-40,height-27,40,18,Component.translatable("gui.done"),b->onClose()));
    }
    private void confirmReroll(TaskNetwork.Row row) {
        if(!DailyTasksClient.rerollable(row)) return;
        String day=DailyTasksClient.snapshot.day();
        minecraft.setScreen(new ConfirmScreen(accepted->{
            if(accepted) DailyTasksClient.reroll(day,row.id());
            minecraft.setScreen(this);
        },Component.translatable("muxi.tasks.reroll_title"),Component.translatable("muxi.tasks.reroll_warning",row.title())) {
            @Override public boolean isPauseScreen() { return false; }
        });
    }
    private TaskNetwork.Row current(String id) {
        var s=DailyTasksClient.snapshot;
        return s==null?null:s.rows().stream().filter(r->r.id().equals(id)).findFirst().orElse(null);
    }
    @Override public void tick() {
        if(minecraft==null || minecraft.player==null) { onClose(); return; }
        if(!signature.equals(signature())) rebuild();
        var s=DailyTasksClient.snapshot;
        if(s!=null) for(int i=0;i<claimButtons.size() && first+i<s.rows().size();i++) {
            claimButtons.get(i).active=s.rows().get(first+i).ready();
            rerollButtons.get(i).active=DailyTasksClient.rerollable(s.rows().get(first+i));
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) { /* No full-screen blur or menu texture. */ }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fillGradient(0,0,Math.min(width,left+column+24),height,0xA010141C,0x5010141C);
        if(tab==Tab.MAINLINE) { renderMainline(g); super.render(g,mx,my,delta); return; }
        g.drawString(font,title,left,top,DailyTasksClient.TEXT,true);
        if(DailyTasksClient.empty()) {
            String message=DailyTasksClient.snapshot!=null && !DailyTasksClient.snapshot.notice().isEmpty()
                ? DailyTasksClient.snapshot.notice():DailyTasksClient.label(DailyTasksClient.supported()?"loading":"unsupported");
            g.drawWordWrap(font,Component.literal(message),left,top+30,column,DailyTasksClient.MUTED);
            super.render(g,mx,my,delta); return;
        }
        var s=DailyTasksClient.snapshot;
        g.drawString(font,DailyTasksClient.label("refresh",DailyTasksClient.remaining()),left,top+15,DailyTasksClient.MUTED,true);
        String allowance=DailyTasksClient.label("rerolls",s.rerollsRemaining());
        g.drawString(font,allowance,left+column-font.width(allowance),top+15,DailyTasksClient.MUTED,true);
        ItemStack hovered=null; String description=null;
        for(int i=first;i<Math.min(s.rows().size(),first+visible);i++) {
            var row=s.rows().get(i); int y=top+33+(i-first)*ROW; String progress=DailyTasksClient.progress(row);
            g.drawString(font,DailyTasksClient.trimmed(font,DailyTasksClient.title(row),column-font.width(progress)-12),left,y,DailyTasksClient.color(row),true);
            g.drawString(font,progress,left+column-font.width(progress),y,DailyTasksClient.color(row),true);
            g.drawString(font,DailyTasksClient.trimmed(font,row.description(),column),left,y+13,DailyTasksClient.MUTED,true);
            ItemStack item=DailyTasksClient.rewardLine(g,row,left,y+28,left+column-98,mx,my); if(item!=null) hovered=item;
            if(mx>=left && mx<left+column && my>=y && my<y+24) description=row.description();
        }
        String footer=!s.notice().isEmpty()?s.notice():s.rows().size()>visible?DailyTasksClient.label("scroll"):"";
        g.drawString(font,DailyTasksClient.trimmed(font,footer,column),left,height-42,DailyTasksClient.MUTED,true);
        super.render(g,mx,my,delta);
        if(hovered!=null) g.renderTooltip(font,hovered,mx,my);
        else if(description!=null) g.renderTooltip(font,Component.literal(description),mx,my);
    }
    private void renderMainline(GuiGraphics g) {
        g.drawString(font,Component.translatable("muxi.mainline.title"),left,top,DailyTasksClient.TEXT,true);
        g.drawString(font,Component.translatable("muxi.mainline.subtitle"),left,top+15,DailyTasksClient.MUTED,true);
        int y=top+42;
        for(MainlineTasks.Entry entry:MainlineTasks.CURRENT) {
            g.drawString(font,entry.title(),left,y,DailyTasksClient.READY,true);
            g.drawWordWrap(font,Component.literal(entry.description()),left,y+16,column,DailyTasksClient.MUTED);
            y+=48;
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(!DailyTasksClient.empty()) {
            int count=DailyTasksClient.snapshot.rows().size();
            int next=Math.max(0,Math.min(Math.max(0,count-visible),first-(int)Math.signum(vertical)));
            if(next!=first) { first=next; rebuild(); return true; }
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(DailyTasksClient.OPEN.matches(key,scan)) { onClose(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    private static final class TextButton extends Button {
        TextButton(int x,int y,int w,int h,Component text,OnPress press) { super(x,y,w,h,text,press,DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
            var font=net.minecraft.client.Minecraft.getInstance().font;
            int color=!active?DailyTasksClient.CLAIMED:isHoveredOrFocused()?0xFFFFFFFF:DailyTasksClient.READY;
            g.drawString(font,getMessage(),getX(),getY()+5,color,true);
            if(active && isHoveredOrFocused()) g.hLine(getX(),getX()+font.width(getMessage())-1,getY()+15,color);
        }
    }
    private static final class TabButton extends Button {
        private final boolean selected;
        TabButton(int x,int y,int w,int h,Component text,OnPress press,boolean selected) {
            super(x,y,w,h,text,press,DEFAULT_NARRATION); this.selected=selected;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
            var font=net.minecraft.client.Minecraft.getInstance().font;
            int color=selected?DailyTasksClient.READY:isHoveredOrFocused()?0xFFFFFFFF:DailyTasksClient.MUTED;
            g.drawString(font,getMessage(),getX(),getY()+5,color,true);
            if(selected || isHoveredOrFocused()) g.hLine(getX(),getX()+font.width(getMessage())-1,getY()+15,color);
        }
    }
}
