package net.muxigame.core.client.tasks;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.muxigame.core.feature.tasks.TaskNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;

public final class DailyTasksClient {
    private DailyTasksClient() {}
    static final int TEXT=0xFFE6EBEF, MUTED=0xFFA1AAB6, READY=0xFFA8D6BA, CLAIMED=0xFF7C8792;
    static final KeyMapping OPEN=new KeyMapping("key.muxi_game_core.daily_tasks",InputConstants.KEY_F8,"key.categories.muxi_game_core");
    private static final float HUD_SCALE=0.65f;
    private static final int HEADER=15, ROW=36, MAINLINE_HEADER=11, MAINLINE_ROW=24;
    private static final int ACTION_WIDTH=34, ACTION_HEIGHT=11, ACTION_GAP=3;
    static TaskNetwork.Snapshot snapshot;
    static TaskHudSettings settings;
    static long receivedAt;
    private static long lastClaim;
    private static int ticks;

    /** Width and height are real GUI pixels; drawing inside is scaled to keep every task compact. */
    private record Box(int x,int y,int width,int height,int visible) {
        boolean contains(double mx,double my) { return mx>=x && mx<x+width && my>=y && my<y+height; }
        int logicalWidth() { return Math.max(1,(int)Math.floor(width/HUD_SCALE)); }
    }

    public static void register(IEventBus modBus,IEventBus gameBus) {
        settings=new TaskHudSettings(Minecraft.getInstance().gameDirectory.toPath());
        TaskNetwork.clientReceiver(DailyTasksClient::receive);
        modBus.addListener(DailyTasksClient::keys); modBus.addListener(DailyTasksClient::layers);
        gameBus.addListener(DailyTasksClient::tick); gameBus.addListener(DailyTasksClient::logout);
        gameBus.addListener(DailyTasksClient::screenRender); gameBus.addListener(DailyTasksClient::screenClick);
        gameBus.addListener(DailyTasksClient::screenKey);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("muxi_game_core","daily_tasks"),(graphics,delta)-> {
            Minecraft mc=Minecraft.getInstance();
            if(!settings.visible || mc.player==null || mc.screen!=null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || !hasContent()) return;
            Box box=box(graphics.guiWidth(),graphics.guiHeight(),settings.left,Math.max(110,graphics.guiWidth()/2-24));
            compact(graphics,box,-1,-1,false);
        });
    }
    private static void receive(TaskNetwork.Snapshot update) {
        snapshot=update; receivedAt=System.nanoTime();
        Minecraft mc=Minecraft.getInstance();
        if(update.open() && mc.player!=null && !(mc.screen instanceof DailyTaskScreen)) mc.setScreen(new DailyTaskScreen());
    }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null) return;
        while(OPEN.consumeClick()) if(mc.screen==null) mc.setScreen(new DailyTaskScreen());
        if(++ticks%60==0 && snapshot==null && supported()) request();
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot=null; receivedAt=0; lastClaim=0; ticks=0; }
    static boolean supported() {
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && NetworkRegistry.hasChannel(connection,TaskNetwork.Request.TYPE.id());
    }
    static boolean empty() { return snapshot==null || snapshot.rows().isEmpty(); }
    private static boolean hasContent() { return !empty() || !MainlineTasks.CURRENT.isEmpty(); }
    static void request() { if(supported()) PacketDistributor.sendToServer(new TaskNetwork.Request(false)); }
    static void claim(TaskNetwork.Row row) {
        long now=System.nanoTime();
        if(row==null || !supported() || snapshot==null || !row.ready() || (lastClaim!=0 && now-lastClaim<300_000_000L)) return;
        lastClaim=now; PacketDistributor.sendToServer(new TaskNetwork.Claim(snapshot.day(),row.id()));
    }
    static boolean rerollable(TaskNetwork.Row row) {
        return snapshot!=null && snapshot.rerollsRemaining()>0 && row!=null && !row.claimed() && row.progress()<row.goal();
    }
    static void reroll(String day,String id) {
        long now=System.nanoTime();
        if(!supported() || snapshot==null || !snapshot.day().equals(day) || (lastClaim!=0 && now-lastClaim<300_000_000L)) return;
        var row=snapshot.rows().stream().filter(r->r.id().equals(id)).findFirst().orElse(null);
        if(!rerollable(row)) return;
        lastClaim=now; PacketDistributor.sendToServer(new TaskNetwork.Reroll(day,id));
    }
    static String remaining() {
        if(snapshot==null || snapshot.resetAt()==0) return "";
        long elapsed=Math.max(0,(System.nanoTime()-receivedAt)/1_000_000_000L);
        long left=Math.max(0,snapshot.resetAt()-snapshot.serverTime()-elapsed);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",left/3600,(left/60)%60,left%60);
    }
    static String label(String key,Object...args) { return Component.translatable("muxi.tasks."+key,args).getString(); }
    static String trimmed(Font font,String text,int width) {
        if(font.width(text)<=width) return text;
        return font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";
    }
    static String progress(TaskNetwork.Row row) { return row.progress()+"/"+row.goal()+row.unit(); }
    static String title(TaskNetwork.Row row) { return row.hard()?label("hard",row.title()):row.title(); }
    static int color(TaskNetwork.Row row) { return row.claimed()?CLAIMED:row.ready()?READY:row.hard()?0xFFE1BA7C:TEXT; }

    private static ItemStack experienceIcon(int levels) {
        ItemStack stack=new ItemStack(Items.EXPERIENCE_BOTTLE);
        stack.set(DataComponents.CUSTOM_NAME,Component.translatable("muxi.tasks.levels",levels));
        return stack;
    }
    static ItemStack rewardLine(GuiGraphics g,TaskNetwork.Row row,int x,int y,int right,int mx,int my) {
        Font font=Minecraft.getInstance().font; ItemStack hovered=null;
        for(ItemStack reward:row.rewards()) {
            if(x+16>right) break;
            g.renderItem(reward,x,y); g.renderItemDecorations(font,reward,x,y);
            if(mx>=x && mx<x+16 && my>=y && my<y+16) hovered=reward;
            x+=18;
        }
        if(row.experienceLevels()>0 && x+16<=right) {
            ItemStack xp=experienceIcon(row.experienceLevels());
            g.renderItem(xp,x,y);
            String amount="+"+row.experienceLevels();
            g.drawString(font,amount,x+16-font.width(amount),y+9,0xFF80FF20,true);
            if(mx>=x && mx<x+16 && my>=y && my<y+16) hovered=xp;
        }
        return hovered;
    }

    private static int logicalHeight() {
        int daily=empty()?0:snapshot.rows().size();
        int height=HEADER+daily*ROW;
        if(!MainlineTasks.CURRENT.isEmpty()) height+=MAINLINE_HEADER+MainlineTasks.CURRENT.size()*MAINLINE_ROW;
        return height;
    }
    private static Box box(int screenWidth,int screenHeight,int preferredLeft,int maxWidth) {
        int width=Math.min(settings.width,Math.max(90,Math.min(maxWidth,screenWidth-8)));
        int x=Math.max(4,Math.min(preferredLeft,screenWidth-width-4));
        int height=Math.max(1,(int)Math.ceil(logicalHeight()*HUD_SCALE));
        int y=Math.max(6,Math.min((int)(screenHeight*settings.topFraction),screenHeight-height-6));
        return new Box(x,y,width,height,empty()?0:snapshot.rows().size());
    }
    private static Box screenBox(Screen screen) {
        if(!settings.visible || !hasContent()) return null;
        int left=settings.left, maxWidth=Math.max(110,screen.width/2-24);
        if(screen instanceof InventoryScreen inventory) {
            int space=inventory.getGuiLeft()-22;
            if(inventory.getRecipeBookComponent().isVisible())
                space-=net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.IMAGE_WIDTH+6;
            if(space>=110) { left=10; maxWidth=space; }
        }
        return box(screen.width,screen.height,left,maxWidth);
    }
    private static boolean supportedScreen(Screen screen) { return screen instanceof InventoryScreen || screen instanceof ChatScreen; }
    private static void screenRender(ScreenEvent.Render.Post event) {
        if(!supportedScreen(event.getScreen()) || Minecraft.getInstance().player==null) return;
        Box box=screenBox(event.getScreen());
        if(box!=null) compact(event.getGuiGraphics(),box,event.getMouseX(),event.getMouseY(),true);
    }
    private static boolean in(double x,double y,int left,int top,int width,int height) {
        return x>=left && x<left+width && y>=top && y<top+height;
    }
    private static int refreshX(int width) { return width-ACTION_WIDTH*2-ACTION_GAP; }
    private static int claimX(int width) { return width-ACTION_WIDTH; }
    private static void screenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen=event.getScreen();
        if(!supportedScreen(screen)) return;
        Box box=screenBox(screen);
        if(box==null || !box.contains(event.getMouseX(),event.getMouseY())) return;
        event.setCanceled(true);
        if(event.getButton()!=0 || empty()) return;
        if(screen instanceof InventoryScreen inventory && !inventory.getMenu().getCarried().isEmpty()) return;
        double mx=(event.getMouseX()-box.x)/HUD_SCALE, my=(event.getMouseY()-box.y)/HUD_SCALE;
        int index=(int)Math.floor((my-HEADER)/ROW);
        if(index<0 || index>=snapshot.rows().size()) return;
        TaskNetwork.Row row=snapshot.rows().get(index);
        int y=HEADER+index*ROW+ROW-ACTION_HEIGHT-1, width=box.logicalWidth();
        if(in(mx,my,claimX(width),y,ACTION_WIDTH,ACTION_HEIGHT)) claim(row);
        else if(in(mx,my,refreshX(width),y,ACTION_WIDTH,ACTION_HEIGHT) && rerollable(row)) confirmReroll(screen,row);
    }
    private static void confirmReroll(Screen parent,TaskNetwork.Row row) {
        if(snapshot==null || !rerollable(row)) return;
        String day=snapshot.day(); Minecraft mc=Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(accepted->{
            if(accepted) reroll(day,row.id());
            mc.setScreen(parent);
        },Component.translatable("muxi.tasks.reroll_title"),Component.translatable("muxi.tasks.reroll_warning",row.title())) {
            @Override public boolean isPauseScreen() { return false; }
        });
    }
    private static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if(event.getScreen() instanceof InventoryScreen && OPEN.matches(event.getKeyCode(),event.getScanCode())) {
            event.setCanceled(true); Minecraft.getInstance().setScreen(new DailyTaskScreen());
        }
    }

    private static void action(GuiGraphics g,Font font,String text,int x,int y,int color,boolean hovered) {
        int shown=hovered?0xFFFFFFFF:color;
        g.drawString(font,text,x+ACTION_WIDTH-font.width(text),y+1,shown,true);
        if(hovered) g.hLine(x+ACTION_WIDTH-font.width(text),x+ACTION_WIDTH-1,y+10,shown);
    }
    private static void compact(GuiGraphics g,Box box,int mouseX,int mouseY,boolean interactive) {
        Font font=Minecraft.getInstance().font;
        int width=box.logicalWidth();
        double mx=interactive?(mouseX-box.x)/HUD_SCALE:-1, my=interactive?(mouseY-box.y)/HUD_SCALE:-1;
        ItemStack hoveredItem=null; String hoveredText=null;
        g.pose().pushPose();
        g.pose().translate(box.x,box.y,0);
        g.pose().scale(HUD_SCALE,HUD_SCALE,1);

        long claimed=empty()?0:snapshot.rows().stream().filter(TaskNetwork.Row::claimed).count();
        String heading=label("title")+(empty()?"":"  "+claimed+"/"+snapshot.rows().size());
        g.drawString(font,heading,0,0,MUTED,true);
        if(!empty()) {
            String countdown=remaining();
            g.drawString(font,countdown,width-font.width(countdown),0,MUTED,true);
            for(int i=0;i<snapshot.rows().size();i++) {
                TaskNetwork.Row row=snapshot.rows().get(i); int y=HEADER+i*ROW; String progress=progress(row);
                g.drawString(font,trimmed(font,title(row),width-font.width(progress)-8),0,y,color(row),true);
                g.drawString(font,progress,width-font.width(progress),y,color(row),true);
                g.drawString(font,trimmed(font,row.description(),width),0,y+9,MUTED,true);

                int actionY=y+ROW-ACTION_HEIGHT-1;
                boolean refreshHover=interactive && in(mx,my,refreshX(width),actionY,ACTION_WIDTH,ACTION_HEIGHT);
                boolean claimHover=interactive && in(mx,my,claimX(width),actionY,ACTION_WIDTH,ACTION_HEIGHT);
                action(g,font,label("refresh_button"),refreshX(width),actionY,rerollable(row)?TEXT:MUTED,refreshHover && rerollable(row));
                String claimText=label(row.claimed()?"claimed":"claim");
                action(g,font,claimText,claimX(width),actionY,row.claimed()?CLAIMED:row.ready()?READY:MUTED,claimHover && row.ready());
                ItemStack item=rewardLine(g,row,0,y+18,refreshX(width)-4,(int)mx,(int)my);
                if(item!=null) hoveredItem=item;
                if(interactive && in(mx,my,0,y,width,ROW) && item==null) hoveredText=row.description();
            }
        }

        int mainY=HEADER+(empty()?0:snapshot.rows().size()*ROW);
        if(!MainlineTasks.CURRENT.isEmpty()) {
            g.drawString(font,Component.translatable("muxi.mainline.title"),0,mainY+1,0xFFE1BA7C,true);
            int y=mainY+MAINLINE_HEADER;
            for(MainlineTasks.Entry entry:MainlineTasks.CURRENT) {
                g.drawString(font,trimmed(font,entry.title(),width),0,y,READY,true);
                g.drawString(font,trimmed(font,entry.description(),width),0,y+10,MUTED,true);
                if(interactive && in(mx,my,0,y,width,MAINLINE_ROW)) hoveredText=entry.description();
                y+=MAINLINE_ROW;
            }
        }
        g.pose().popPose();
        if(interactive) {
            if(hoveredItem!=null) g.renderTooltip(font,hoveredItem,mouseX,mouseY);
            else if(hoveredText!=null) g.renderTooltip(font,Component.literal(hoveredText),mouseX,mouseY);
        }
    }
}
