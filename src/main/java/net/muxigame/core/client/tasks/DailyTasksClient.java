package net.muxigame.core.client.tasks;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.muxigame.core.feature.tasks.TaskNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;

public final class DailyTasksClient {
    private DailyTasksClient() {}
    static final int TEXT=0xFFE6EBEF, MUTED=0xFFA1AAB6, READY=0xFFA8D6BA, CLAIMED=0xFF7C8792;
    static final KeyMapping OPEN=new KeyMapping("key.muxi_game_core.daily_tasks",InputConstants.KEY_F8,"key.categories.muxi_game_core");
    static TaskNetwork.Snapshot snapshot;
    static TaskHudSettings settings;
    static long receivedAt;
    private static long lastClaim;
    private static int ticks;
    private record Box(int x,int y,int width,int height,int visible) {
        boolean contains(double mx,double my) { return mx>=x && mx<x+width && my>=y && my<y+height; }
    }
    public static void register(IEventBus modBus,IEventBus gameBus) {
        settings=new TaskHudSettings(Minecraft.getInstance().gameDirectory.toPath());
        TaskNetwork.clientReceiver(DailyTasksClient::receive);
        modBus.addListener(DailyTasksClient::keys); modBus.addListener(DailyTasksClient::layers);
        gameBus.addListener(DailyTasksClient::tick); gameBus.addListener(DailyTasksClient::logout);
        gameBus.addListener(DailyTasksClient::inventoryRender); gameBus.addListener(DailyTasksClient::inventoryClick);
        gameBus.addListener(DailyTasksClient::inventoryKey);
    }
    private static void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("muxi_game_core","daily_tasks"),(graphics,delta)-> {
            Minecraft mc=Minecraft.getInstance();
            if(!settings.visible || mc.player==null || mc.screen!=null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || empty()) return;
            int w=graphics.guiWidth(), h=graphics.guiHeight();
            int width=Math.min(settings.width,Math.max(120,w/2-36));
            int left=Math.min(settings.left,Math.max(4,w/2-width-12));
            int visible=Math.min(snapshot.rows().size(),Math.max(1,(h-70)/38));
            int height=30+visible*38;
            int y=Math.max(8,Math.min((int)(h*settings.topFraction),h-height-34));
            compact(graphics,new Box(left,y,width,height,visible),-1,-1,false);
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
    static ItemStack rewardLine(GuiGraphics g,TaskNetwork.Row row,int x,int y,int right,int mx,int my) {
        Font font=Minecraft.getInstance().font; ItemStack hovered=null;
        for(ItemStack reward:row.rewards()) {
            g.renderItem(reward,x,y); g.renderItemDecorations(font,reward,x,y);
            if(mx>=x && mx<x+16 && my>=y && my<y+16) hovered=reward;
            x+=20;
        }
        String xp=label("levels",row.experienceLevels());
        if(row.experienceLevels()>0 && x+font.width(xp)<right) g.drawString(font,xp,x+2,y+5,MUTED,true);
        return hovered;
    }
    private static Box inventoryBox(InventoryScreen screen) {
        int space=screen.getGuiLeft()-22;
        if(screen.getRecipeBookComponent().isVisible())
            space-=net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.IMAGE_WIDTH+6;
        if(empty() || !settings.visible || space<120) return null;
        int visible=Math.min(snapshot.rows().size(),Math.max(1,(screen.height-70)/38));
        int height=30+visible*38;
        return new Box(10,Math.max(10,Math.min(screen.getGuiTop(),screen.height-height-10)),Math.min(settings.width,space),height,visible);
    }
    private static void inventoryRender(ScreenEvent.Render.Post event) {
        if(!(event.getScreen() instanceof InventoryScreen screen)) return;
        Box box=inventoryBox(screen);
        if(box!=null) compact(event.getGuiGraphics(),box,event.getMouseX(),event.getMouseY(),true);
    }
    private static void inventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if(!(event.getScreen() instanceof InventoryScreen screen)) return;
        Box box=inventoryBox(screen);
        if(box==null || !box.contains(event.getMouseX(),event.getMouseY())) return;
        // The sidebar is NOT an inventory slot or an outside-drop target. Never drop a carried stack here.
        event.setCanceled(true);
        if(event.getButton()!=0 || !screen.getMenu().getCarried().isEmpty()) return;
        for(int i=0;i<box.visible;i++) {
            int y=box.y+17+i*38;
            if(event.getMouseX()>=box.x+box.width-36 && event.getMouseY()>=y+12 && event.getMouseY()<y+32)
                claim(snapshot.rows().get(i));
        }
    }
    private static void inventoryKey(ScreenEvent.KeyPressed.Pre event) {
        if(event.getScreen() instanceof InventoryScreen && OPEN.matches(event.getKeyCode(),event.getScanCode())) {
            event.setCanceled(true); Minecraft.getInstance().setScreen(new DailyTaskScreen());
        }
    }
    private static void compact(GuiGraphics g,Box box,int mx,int my,boolean interactive) {
        Font font=Minecraft.getInstance().font;
        long claimed=snapshot.rows().stream().filter(TaskNetwork.Row::claimed).count();
        g.drawString(font,label("title")+"  "+claimed+"/"+snapshot.rows().size(),box.x,box.y,MUTED,true);
        ItemStack hovered=null; String description=null;
        for(int i=0;i<box.visible;i++) {
            var row=snapshot.rows().get(i); int y=box.y+17+i*38; String progress=progress(row);
            g.drawString(font,trimmed(font,title(row),box.width-font.width(progress)-10),box.x,y,color(row),true);
            g.drawString(font,progress,box.x+box.width-font.width(progress),y,color(row),true);
            ItemStack item=rewardLine(g,row,box.x,y+13,box.x+box.width-38,mx,my); if(item!=null) hovered=item;
            if(row.ready() || row.claimed()) {
                String status=label(row.claimed()?"claimed":interactive?"claim":"ready");
                g.drawString(font,status,box.x+box.width-font.width(status),y+18,color(row),true);
            }
            if(interactive && mx>=box.x && mx<box.x+box.width && my>=y && my<y+11) description=row.description();
        }
        String hint=label("hint",OPEN.getTranslatedKeyMessage().getString());
        if(box.visible<snapshot.rows().size()) hint="+"+(snapshot.rows().size()-box.visible)+"  "+hint;
        g.drawString(font,trimmed(font,hint,box.width),box.x,box.y+18+box.visible*38,MUTED,true);
        if(interactive) {
            if(hovered!=null) g.renderTooltip(font,hovered,mx,my);
            else if(description!=null) g.renderTooltip(font,Component.literal(description),mx,my);
        }
    }
}
