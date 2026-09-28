package net.muxigame.core.client.challenge;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.muxigame.core.feature.challenge.*;
import static net.muxigame.core.client.challenge.ChallengeClient.*;

/** Paginated lobby: no clickable UUIDs, all display labels use server-supplied nicknames. */
public final class ChallengeScreen extends Screen {
    private enum Tab { LOBBY, TASKS, SHOP, LOADOUT, MAP }
    private Tab tab=Tab.LOBBY;
    private ChallengeRules.Difficulty difficulty=ChallengeRules.Difficulty.NORMAL;
    private int page,tick,left,top;
    private String signature="";
    public ChallengeScreen(){super(Component.literal("挑战任务 · 僵尸入侵"));}
    public boolean isPauseScreen(){return false;}
    protected void init(){left=Math.max(10,(width-380)/2);top=42;rebuild();action("list","");}
    private void button(String text,int x,int y,int w,Runnable click){addRenderableWidget(Button.builder(Component.literal(text),b->click.run()).bounds(x,y,w,18).build());}
    private String signature(){return tab+":"+array("rooms").toString().replaceAll("\"seconds\":\\d+","\"seconds\":0")+array("players")+array("tasks")+array("weapons")+number(state,"credits")+number(state,"primary")+":"+number(state,"secondary")+number(state,"rewards");}
    private void rebuild(){
        clearWidgets();signature=signature();
        button("大厅",left,20,54,()->{tab=Tab.LOBBY;page=0;rebuild();});button("任务",left+58,20,54,()->{tab=Tab.TASKS;page=0;rebuild();});
        button("积分商店",left+116,20,68,()->{tab=Tab.SHOP;page=0;rebuild();});button("携带武器",left+188,20,68,()->{tab=Tab.LOADOUT;page=0;rebuild();});
        button("地图",left+260,20,54,()->{tab=Tab.MAP;rebuild();});button("返回",left+318,20,54,this::onClose);
        if(!supported() && !flag(state,"available"))return;
        if(tab==Tab.LOBBY){
            JsonObject room=mine();
            if(room==null){
                button("难度："+difficulty.title+" / "+difficulty.waves+" 波",left,top+18,175,()->{difficulty=ChallengeRules.Difficulty.values()[(difficulty.ordinal()+1)%4];rebuild();});
                button("创建房间",left+185,top+18,90,()->action("create",difficulty.name()));
                int y=top+65;for(JsonElement e:array("rooms")){JsonObject r=e.getAsJsonObject();String id=text(r,"id");Button b=Button.builder(Component.literal(flag(r,"invited")?"接受邀请":"仅限邀请"),it->action("join",id)).bounds(left+280,y,90,18).build();b.active=flag(r,"invited") && (text(r,"phase").equals("LOBBY")||text(r,"phase").equals("BUILDING"));addRenderableWidget(b);y+=30;}
            }else{
                boolean host=text(room,"host").equals(text(state,"self"));String phase=text(room,"phase");
                button("离开房间",left+275,top+18,95,()->minecraft.setScreen(new ConfirmScreen(yes->{if(yes)action("leave","");minecraft.setScreen(this);},Component.literal("离开挑战？"),Component.literal("开局后离场将放弃本局通关奖励，原背包会恢复。")){public boolean isPauseScreen(){return false;}}));
                if(host && phase.equals("LOBBY")){
                    button("开始（支持单人）",left,top+45,150,()->action("start",""));
                    button("难度："+ChallengeRules.Difficulty.parse(text(room,"difficulty")).title,left+160,top+45,110,()->action("difficulty",ChallengeRules.Difficulty.values()[(ChallengeRules.Difficulty.parse(text(room,"difficulty")).ordinal()+1)%4].name()));
                }
                if(host && (phase.equals("LOBBY")||phase.equals("BUILDING"))){
                    int count=Math.max(1,(height-top-140)/23),start=page*count;
                    for(int i=start;i<Math.min(array("players").size(),start+count);i++){JsonObject q=array("players").get(i).getAsJsonObject();button("邀请 "+text(q,"name"),left,top+95+(i-start)*23,260,()->action("invite",text(q,"id")));}
                    button("上一页",left,height-40,75,()->{page=Math.max(0,page-1);rebuild();});button("下一页",left+80,height-40,75,()->{page=Math.min(Math.max(0,(array("players").size()-1)/count),page+1);rebuild();});
                }
            }
        }else if(tab==Tab.TASKS){
            for(int i=0;i<array("tasks").size();i++){final int index=i;JsonObject task=array("tasks").get(i).getAsJsonObject();Button b=Button.builder(Component.literal(flag(task,"claimed")?"已领取":"领取任务奖励"),it->action("task",Integer.toString(index))).bounds(left+270,top+20+i*36,100,18).build();b.active=flag(task,"ready")&&!flag(task,"claimed");addRenderableWidget(b);}
            if(number(state,"rewards")>0)button("领取旧版奖励（"+number(state,"rewards")+" 组）",left,top+142,230,()->action("claim",""));
        }else if(tab==Tab.SHOP){
            int count=Math.max(1,(height-top-80)/27),start=page*count;
            for(int i=start;i<Math.min(array("shop").size(),start+count);i++){
                JsonObject offer=array("shop").get(i).getAsJsonObject();String order=text(offer,"id")+":"+number(state,"shopRevision");
                Button b=Button.builder(Component.literal("购买 · "+number(offer,"cost")+" 分"),it->action("buy",order)).bounds(left+245,top+30+(i-start)*27,125,18).build();
                b.active=!flag(state,"locked")&&number(state,"credits")>=number(offer,"cost");addRenderableWidget(b);
            }
            pages(array("shop").size(),count);
        }else if(tab==Tab.LOADOUT){
            if(!flag(state,"locked")){
                button("主武器：新手默认",left,top+28,180,()->action("primary","-1"));button("副武器：新手默认",left+190,top+28,180,()->action("secondary","-1"));
                int count=Math.max(1,(height-top-130)/30),start=page*count;
                for(int i=start;i<Math.min(array("weapons").size(),start+count);i++){
                    JsonObject w=array("weapons").get(i).getAsJsonObject();String slot=Integer.toString(number(w,"slot"));int y=top+77+(i-start)*30;
                    button(number(state,"primary")==number(w,"slot")?"已选主":"设为主",left+240,y,62,()->action("primary",slot));
                    button(number(state,"secondary")==number(w,"slot")?"已选副":"设为副",left+308,y,62,()->action("secondary",slot));
                }
                pages(array("weapons").size(),count);
            }
        }
    }
    private void pages(int size,int count){button("上一页",left,height-40,75,()->{page=Math.max(0,page-1);rebuild();});button("下一页",left+80,height-40,75,()->{page=Math.min(Math.max(0,(size-1)/count),page+1);rebuild();});}
    public void tick(){if(++tick%40==0)action("list","");if(!signature.equals(signature()))rebuild();}
    public void renderBackground(GuiGraphics g,int x,int y,float dt){}
    public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xDC101720);
        if(!supported() && !flag(state,"available"))g.drawWordWrap(font,Component.literal("服务端尚未启用挑战模式，请等待新版服务端生效。"),left,top+20,360,0xFFE0B080);
        else if(tab==Tab.LOBBY){
            JsonObject r=mine();
            if(r==null){g.drawString(font,"封锁研究所 · 三层 / 12 房间 · 每队 1–4 人",left,top,0xFFE6EBEF);g.drawString(font,"房间列表（由房主邀请后加入）",left,top+48,0xFFA1AAB6);int y=top+67;for(JsonElement e:array("rooms")){JsonObject q=e.getAsJsonObject();g.drawString(font,font.plainSubstrByWidth(text(q,"name")+" · "+ChallengeRules.Difficulty.parse(text(q,"difficulty")).title+" · "+number(q,"count")+"/4",265),left,y,0xFFE6EBEF);g.drawString(font,phase(text(q,"phase")),left,y+11,0xFFA1AAB6);y+=30;}}
            else {g.drawString(font,"房间 "+text(r,"id")+" · "+phase(text(r,"phase")),left,top,0xFF8AF0A8);g.drawString(font,"队伍 "+number(r,"count")+"/4 · "+ChallengeRules.Difficulty.parse(text(r,"difficulty")).title,left,top+23,0xFFE6EBEF);
                if(!text(r,"phase").equals("LOBBY")&&!text(r,"phase").equals("BUILDING")){g.drawString(font,"波次 "+number(r,"wave")+"/"+number(r,"total")+" · 敌人 "+number(r,"remaining")+" · "+number(r,"seconds")+" 秒",left,top+55,0xFFE6EBEF);g.drawWordWrap(font,Component.literal("原背包已保管。战斗中装备栏锁定，用数字键切换武器；右键彩色地面补给点。阵亡或离场恢复背包。"),left,top+80,360,0xFFA1AAB6);}
                else g.drawString(font,"房主邀请在线玩家：",left,top+78,0xFFA1AAB6);
            }
        }else if(tab==Tab.TASKS){
            g.drawString(font,"通关 "+number(state,"wins")+" 次 · 击杀 "+number(state,"kills")+" · 最高分 "+number(state,"best"),left,top,0xFFE6EBEF);
            String[] rewards={"挑战积分 +350","挑战积分 +600","挑战积分 +1200"};
            for(int i=0;i<array("tasks").size();i++){JsonObject t=array("tasks").get(i).getAsJsonObject();g.drawString(font,text(t,"title"),left,top+22+i*36,flag(t,"ready")?0xFF8AF0A8:0xFFE6EBEF);g.drawString(font,rewards[i],left,top+34+i*36,0xFFA1AAB6);}
            g.drawString(font,font.plainSubstrByWidth(text(state,"last"),365),left,top+126,0xFFE1BA7C);
            g.drawWordWrap(font,Component.literal("击杀得分、爆头击杀额外加分；通关追加积分。积分跨局保留，离场后在积分商店自由购买物品。当前余额："+number(state,"credits")),left,top+168,360,0xFFA1AAB6);
        }else if(tab==Tab.SHOP){
            g.drawString(font,"可用积分："+number(state,"credits")+" · 本局已得："+number(state,"earned"),left,top,0xFF8AF0A8);
            int count=Math.max(1,(height-top-80)/27),start=page*count;
            for(int i=start;i<Math.min(array("shop").size(),start+count);i++)g.drawString(font,text(array("shop").get(i).getAsJsonObject(),"title"),left,top+35+(i-start)*27,0xFFE6EBEF);
            if(flag(state,"locked"))g.drawString(font,"离场后可购买；阵亡保留已获击杀积分。",left,top+14,0xFFE1BA7C);
        }else if(tab==Tab.LOADOUT){
            g.drawString(font,"主武器："+selectedName("primary")+" · 副武器："+selectedName("secondary"),left,top,0xFF8AF0A8);
            if(flag(state,"locked"))g.drawString(font,"本局已锁定装备，结束后再选择。",left,top+28,0xFFE1BA7C);
            else{
                g.drawString(font,"完整保留改装组件；开局后恢复记录保管原件。",left,top+55,0xFFA1AAB6);
                int count=Math.max(1,(height-top-130)/30),start=page*count;
                for(int i=start;i<Math.min(array("weapons").size(),start+count);i++){JsonObject w=array("weapons").get(i).getAsJsonObject();g.drawString(font,font.plainSubstrByWidth("槽 "+(number(w,"slot")+1)+" · "+text(w,"name"),230),left,top+82+(i-start)*30,0xFFE6EBEF);}
            }
        }else{
            String text="封锁研究所：三层 / 12 房间\n一层：接待大厅、检疫室、安保室、物资仓\n二层：实验室 A / B、医疗站、电源室\n三层：指挥室、服务器机房、隔离舱、屋顶防线\n\n中央十字走廊连接四间房，各角落为僵尸刷新点。\n右键紫珀块上楼 / 青金石块下楼；僵尸会跨层追击。\n金块补弹药（冷却 30 秒），数量随难度增加。\n绿宝石块医疗 / 食物，红石块道具：每人每波一次。\n波间休整 12 秒。每 3 波疾行，每 4 波装甲突袭。\n每 5 波感染暴君：紫色血条，注意蓄力范围攻击！\n单波限时 5 分钟，阵亡淘汰；全部淘汰即失败。\n临时枪械、弓箭和护甲入场配发，离场恢复原背包。";
            g.drawWordWrap(font,Component.literal(text),left,top,365,0xFFE6EBEF);
        }
        String notice=text(state,"notice");if(!notice.isEmpty())g.drawString(font,font.plainSubstrByWidth(notice,370),left,height-16,0xFFE1BA7C);
        super.render(g,mx,my,dt);
    }
    private String selectedName(String key){int slot=state.has(key)?number(state,key):-1;for(JsonElement e:array("weapons")){JsonObject w=e.getAsJsonObject();if(number(w,"slot")==slot)return font.plainSubstrByWidth(text(w,"name"),100);}return slot<0?"新手默认":"槽 "+(slot+1);}
    private static String phase(String phase){return switch(phase){case "BUILDING"->"地图生成中";case "LOBBY"->"等待开局";case "COUNTDOWN"->"准备倒计时";case "RUNNING"->"战斗中";case "REST"->"波间补给";default->phase;};}
}
