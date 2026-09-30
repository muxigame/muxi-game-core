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
    static final int TEXT=0xFFE6EBEF, MUTED=0xFFA1AAB6, READY=0xFF8AF0A8, CLAIMED=0xFF6FD08B;
    static final KeyMapping OPEN=new KeyMapping("key.muxi_game_core.daily_tasks",InputConstants.KEY_F8,"key.categories.muxi_game_core");
    private static final ResourceLocation EXPERIENCE_ORB_TEXTURE=ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final float HUD_SCALE=0.65f;
    private static final int TOP_BAR=20, SECTION_HEADER=15, ROW=36;
    private static final int ACTION_WIDTH=34, ACTION_HEIGHT=11, ACTION_GAP=3;
    private static final int TASK_BUTTON_WIDTH=48, TASK_BUTTON_HEIGHT=18;
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
        settings.pruneTracked(update.rows().stream().map(TaskNetwork.Row::id).toList());
        Minecraft mc=Minecraft.getInstance();
        if(update.open() && mc.player!=null && !(mc.screen instanceof DailyTaskScreen)) openTaskApp();
    }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null) return;
        while(OPEN.consumeClick()) if(mc.screen==null) openTaskApp();
        if(++ticks%60==0 && snapshot==null && supported()) request();
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot=null; receivedAt=0; lastClaim=0; ticks=0; }
    static boolean supported() {
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && NetworkRegistry.hasChannel(connection,TaskNetwork.Request.TYPE.id());
    }
    static boolean empty() { return snapshot==null || snapshot.rows().isEmpty(); }
    private static boolean hasContent() { return snapshot!=null || supported(); }
    static List<TaskNetwork.Row> trackedRows() {
        if(snapshot==null) return List.of();
        return snapshot.rows().stream().filter(r->settings.isTracked(r.id())).toList();
    }
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
        String amount="+"+row.experienceLevels();
        if(row.experienceLevels()>0 && x+18+font.width(amount)<=right) {
            ItemStack xp=experienceIcon(row.experienceLevels());
            g.blit(EXPERIENCE_ORB_TEXTURE,x,y,0,0,16,16,64,64);
            g.drawString(font,amount,x+18,y+4,0xFF80FF20,true);
            if(mx>=x && mx<x+18+font.width(amount) && my>=y && my<y+16) hovered=xp;
        }
        return hovered;
    }

    private static int logicalHeight() {
        List<TaskNetwork.Row> tracked=trackedRows();
        return TOP_BAR+SECTION_HEADER+(tracked.isEmpty()?14:tracked.size()*ROW);
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
        if(event.getButton()!=0) return;
        double mx=(event.getMouseX()-box.x)/HUD_SCALE, my=(event.getMouseY()-box.y)/HUD_SCALE;
        int width=box.logicalWidth();
        if(in(mx,my,0,0,TASK_BUTTON_WIDTH,TASK_BUTTON_HEIGHT)) {
            event.setCanceled(true);
            if(screen instanceof InventoryScreen inventory && !inventory.getMenu().getCarried().isEmpty()) return;
            openTaskApp();
            return;
        }
        List<TaskNetwork.Row> tracked=trackedRows();
        if(tracked.isEmpty()) return;
        int index=(int)Math.floor((my-(TOP_BAR+SECTION_HEADER))/ROW);
        if(index<0 || index>=tracked.size()) return;
        if(screen instanceof InventoryScreen inventory && !inventory.getMenu().getCarried().isEmpty()) return;
        TaskNetwork.Row row=tracked.get(index);
        int y=TOP_BAR+SECTION_HEADER+index*ROW+ROW-ACTION_HEIGHT-1;
        if(in(mx,my,claimX(width),y,ACTION_WIDTH,ACTION_HEIGHT)) { event.setCanceled(true); claim(row); }
        else if(in(mx,my,refreshX(width),y,ACTION_WIDTH,ACTION_HEIGHT) && rerollable(row)) { event.setCanceled(true); confirmReroll(screen,row); }
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
            event.setCanceled(true); openTaskApp();
        }
    }

    private static void openTaskApp() {
        try {
            Class<?> terminal=Class.forName("net.muxigame.terminal.client.TerminalClient");
            terminal.getMethod("openApp",String.class).invoke(null,"tasks");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            Minecraft.getInstance().setScreen(new DailyTaskScreen());
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
        List<TaskNetwork.Row> tracked=trackedRows();
        g.pose().pushPose();
        g.pose().translate(box.x,box.y,0);
        g.pose().scale(HUD_SCALE,HUD_SCALE,1);

        boolean taskHover=interactive && in(mx,my,0,0,TASK_BUTTON_WIDTH,TASK_BUTTON_HEIGHT);
        g.fill(0,0,TASK_BUTTON_WIDTH,TASK_BUTTON_HEIGHT,taskHover?0xB050565E:0x90343A42);
        g.fill(0,TASK_BUTTON_HEIGHT-1,TASK_BUTTON_WIDTH,TASK_BUTTON_HEIGHT,taskHover?0xFFFFFFFF:0xFF8A939E);
        g.renderItem(new ItemStack(Items.WRITABLE_BOOK),1,1);
        g.drawString(font,label("open_button"),19,5,taskHover?0xFFFFFFFF:TEXT,true);

        int headerY=TOP_BAR;
        long completed=tracked.stream().filter(r->r.progress()>=r.goal()).count();
        String heading=label("tracked_title")+"  "+completed+"/"+tracked.size();
        g.drawString(font,trimmed(font,heading,width),0,headerY,MUTED,true);
        if(!tracked.isEmpty()) {
            String countdown=remaining();
            if(!countdown.isEmpty() && font.width(heading)+font.width(countdown)+8<width)
                g.drawString(font,countdown,width-font.width(countdown),headerY,MUTED,true);
        }
        int rowsY=TOP_BAR+SECTION_HEADER;
        if(tracked.isEmpty()) {
            g.drawString(font,trimmed(font,label("tracked_empty"),width),3,rowsY+2,MUTED,true);
        } else for(int i=0;i<tracked.size();i++) {
            TaskNetwork.Row row=tracked.get(i); int y=rowsY+i*ROW; String progress=progress(row);
            boolean complete=row.progress()>=row.goal();
            if(complete) {
                g.fill(-2,y-2,width+2,y+ROW-2,row.claimed()?0x30347643:0x503B8F50);
                g.fill(-2,y-2,1,y+ROW-2,row.claimed()?CLAIMED:READY);
            }
            String rowTitle=(complete?"✓ ":"")+title(row);
            g.drawString(font,trimmed(font,rowTitle,width-font.width(progress)-8),3,y,color(row),true);
            g.drawString(font,progress,width-font.width(progress),y,color(row),true);
            g.drawString(font,trimmed(font,row.description(),width-3),3,y+9,complete?0xFFD5F6DC:MUTED,true);
            int actionY=y+ROW-ACTION_HEIGHT-1;
            boolean refreshHover=interactive && in(mx,my,refreshX(width),actionY,ACTION_WIDTH,ACTION_HEIGHT);
            boolean claimHover=interactive && in(mx,my,claimX(width),actionY,ACTION_WIDTH,ACTION_HEIGHT);
            action(g,font,label("refresh_button"),refreshX(width),actionY,rerollable(row)?TEXT:MUTED,refreshHover && rerollable(row));
            String claimText=label(row.claimed()?"claimed":"claim");
            action(g,font,claimText,claimX(width),actionY,row.claimed()?CLAIMED:row.ready()?READY:MUTED,claimHover && row.ready());
            ItemStack item=rewardLine(g,row,3,y+18,refreshX(width)-4,(int)mx,(int)my);
            if(item!=null) hoveredItem=item;
            if(interactive && in(mx,my,0,y,width,ROW) && item==null) hoveredText=row.description();
        }
        g.pose().popPose();
        if(interactive) {
            if(taskHover) g.renderTooltip(font,Component.translatable("muxi.tasks.open_tooltip"),mouseX,mouseY);
            else if(hoveredItem!=null) g.renderTooltip(font,hoveredItem,mouseX,mouseY);
            else if(hoveredText!=null) g.renderTooltip(font,Component.literal(hoveredText),mouseX,mouseY);
        }
    }
}
