package net.muxigame.core.taskssmoke;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import net.muxigame.core.client.tasks.DailyTaskScreen;
import net.muxigame.core.feature.tasks.TaskNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Native-render fixture only. Does not join a server, impersonate a real account, or grant actual rewards. */
@Mod(value="muxi_tasks_client_smoke",dist=Dist.CLIENT)
public final class TaskClientSmoke {
    private int ticks;
    private boolean started;
    public TaskClientSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(started || ++ticks<40 || mc.getOverlay()!=null || mc.screen==null) return;
        started=true;
        try {
            ItemStack sword=new ItemStack(Items.IRON_SWORD);
            sword.set(DataComponents.CUSTOM_NAME,Component.literal("巡夜者的铁剑"));
            sword.set(DataComponents.DAMAGE,12); sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            sword.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("完成每日巡逻的纪念。"))));
            List<TaskNetwork.Row> rows=List.of(
                new TaskNetwork.Row("iron_miner","矿洞寻铁","挖掘 16 块铁矿石或深层铁矿石。",9,16,"",false,List.of(new ItemStack(Items.EMERALD,4),new ItemStack(Items.COAL,16)),1),
                new TaskNetwork.Row("skeleton_patrol","清理弓手","击败 18 只骷髅；自己的女仆、傀儡和宠物代劳也计入。",18,18,"",false,List.of(new ItemStack(Items.ARROW,64),new ItemStack(Items.IRON_INGOT,4)),1),
                new TaskNetwork.Row("harvest_crops","田园收获","收获任意成熟作物 12 株，支持破坏收割和右键收获；种子不另计。",5,12,"株",false,List.of(new ItemStack(Items.EMERALD,8)),1),
                new TaskNetwork.Row("legendary_fairy","传奇狩猎·夜空","击败传奇（4阶）及以上的女仆妖精 或 幻翼任意 1 只；二选一，辅助击杀也计入。",0,1,"",false,List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE,2),new ItemStack(Items.EMERALD,8)),1,true));
            var packet=new TaskNetwork.Snapshot("2026-09-27",Instant.now().getEpochSecond()+17220,Instant.now().getEpochSecond(),rows,false,"");
            Class<?> client=Class.forName("net.muxigame.core.client.tasks.DailyTasksClient");
            Method receive=client.getDeclaredMethod("receive",TaskNetwork.Snapshot.class); receive.setAccessible(true); receive.invoke(null,packet);
            mc.setScreen(new Preview(client));
        } catch(Throwable e) { failure(e); }
    }
    private static void failure(Throwable e) {
        e.printStackTrace();
        try { Files.writeString(Path.of("client-smoke-result.json"),"{\"success\":false,\"error\":"+new com.google.gson.Gson().toJson(e.toString())+"}"); }
        catch(Exception ignored) {}
        Minecraft.getInstance().stop();
    }
    private static final class Preview extends Screen {
        private final DailyTaskScreen detail=new DailyTaskScreen();
        private final net.muxigame.core.client.challenge.ChallengeScreen challenge=new net.muxigame.core.client.challenge.ChallengeScreen();
        private final Class<?> client;
        private Method compact;
        private Object box;
        private int boxY;
        private int frames;
        private boolean mainlineShown;
        Preview(Class<?> client) { super(Component.literal("muxi daily tasks native-render QA")); this.client=client; }
        @Override protected void init() {
            try {
                detail.init(minecraft,width,height);
                net.muxigame.core.client.challenge.ChallengeClient.state=com.google.gson.JsonParser.parseString("""
                    {"available":true,"self":"qa","wins":2,"kills":140,"best":6200,"rewards":0,"credits":6200,"earned":300,"primary":5,"secondary":-1,"last":"通关 · 困难 · 6200 分 · B 级",
                     "shop":[{"id":"diamond","title":"钻石 ×2","cost":400},{"id":"glock","title":"格洛克 17","cost":2500},{"id":"ak47","title":"AK47","cost":6000}],
                     "weapons":[{"slot":5,"name":"改装 AK47 · 保留瞄具 / 弹匣"},{"slot":8,"name":"改装格洛克 17"}],
                     "rooms":[{"id":"qa-room","host":"other","name":"研究所突击队","difficulty":"HARD","phase":"LOBBY","wave":0,"total":10,"count":2,"mine":false,"invited":true}],
                     "players":[],"tasks":[{"title":"首次防线：完成一次挑战","ready":true,"claimed":false},{"title":"清剿行动：累计击败 100 只入侵僵尸","ready":true,"claimed":true},{"title":"精英防线：完成困难及以上挑战","ready":true,"claimed":false}]}
                    """).getAsJsonObject();
                challenge.init(minecraft,width,height);
                Class<?> boxType=Class.forName("net.muxigame.core.client.tasks.DailyTasksClient$Box");
                Constructor<?> ctor=boxType.getDeclaredConstructor(int.class,int.class,int.class,int.class,int.class);
                boxY=Math.max(12,Math.min(height/3,height-150));
                ctor.setAccessible(true); box=ctor.newInstance(12,boxY,190,128,4);
                compact=client.getDeclaredMethod("compact",GuiGraphics.class,boxType,int.class,int.class,boolean.class); compact.setAccessible(true);
            } catch(Exception e) { failure(e); }
        }
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {}
        @Override public void tick() { /* Deliberately do not tick the production screen: this fixture has no world/player. */ }
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            try {
                if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(minecraft.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=org.lwjgl.glfw.GLFW.GLFW_FALSE)
                    throw new AssertionError("Native render test window must remain invisible");
                g.fillGradient(0,0,width,height,0xFF25313D,0xFF101820);
                if(frames<40) compact.invoke(null,g,box,36,boxY+19,true);
                else if(frames<80) {
                    if(!mainlineShown) {
                        Field tab=DailyTaskScreen.class.getDeclaredField("tab"); tab.setAccessible(true);
                        Object mainline=Enum.valueOf((Class<Enum>)tab.getType(),"MAINLINE"); tab.set(detail,mainline);
                        Method rebuild=DailyTaskScreen.class.getDeclaredMethod("rebuild"); rebuild.setAccessible(true); rebuild.invoke(detail);
                        mainlineShown=true;
                    }
                    detail.render(g,16,228,delta);
                }
                else {
                    if(frames==120){Field tab=challenge.getClass().getDeclaredField("tab");tab.setAccessible(true);tab.set(challenge,Enum.valueOf((Class<Enum>)tab.getType(),"TASKS"));Method rebuild=challenge.getClass().getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(challenge);}
                    if(frames==160 || frames==200){Field tab=challenge.getClass().getDeclaredField("tab");tab.setAccessible(true);tab.set(challenge,Enum.valueOf((Class<Enum>)tab.getType(),frames==160?"SHOP":"LOADOUT"));Method rebuild=challenge.getClass().getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(challenge);}
                    challenge.render(g,16,228,delta);
                }
                Component note=Component.literal("本地界面测试 · 示例任务");
                g.drawString(font,note,width-font.width(note)-12,height-18,0xFF81909C,true);
                frames++;
                if(frames==30 || frames==70 || frames==110 || frames==150 || frames==190 || frames==230) {
                    g.flush();
                    try(var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                        image.writeToFile(Path.of(frames==30?"task-hud.png":frames==70?"task-mainline.png":frames==110?"challenge-lobby.png":frames==150?"challenge-tasks.png":frames==190?"challenge-shop.png":"challenge-loadout.png"));
                    }
                }
                if(frames==240) {
                    Files.writeString(Path.of("client-smoke-result.json"),"{\"success\":true,\"windowVisible\":false,\"nativeFrames\":240,\"screenshots\":[\"task-hud.png\",\"task-mainline.png\",\"challenge-lobby.png\",\"challenge-tasks.png\",\"challenge-shop.png\",\"challenge-loadout.png\"],\"fixture\":\"hidden native render, early window disabled; synthetic data, no server or player\"}");
                    minecraft.stop();
                }
            } catch(Throwable e) { failure(e); }
        }
    }
}
