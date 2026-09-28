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
    private String signature(){return tab+":"+array("rooms").toString().replaceAll("\"seconds\":\\d+","\"seconds\":0")+array("players")+array("tasks")+array("weapons")+array("shop")+number(state,"coins")+number(state,"primary")+":"+number(state,"primary2")+":"+number(state,"secondary")+":"+number(state,"carryFee")+number(state,"rewards");}
    private void rebuild(){
        clearWidgets();signature=signature();
        button("大厅",left,20,54,()->{tab=Tab.LOBBY;page=0;rebuild();});button("任务",left+58,20,54,()->{tab=Tab.TASKS;page=0;rebuild();});
        button("兑换商店",left+116,20,68,()->{tab=Tab.SHOP;page=0;rebuild();});button("携带武器",left+188,20,68,()->{tab=Tab.LOADOUT;page=0;rebuild();});
        button("地图",left+260,20,54,()->{tab=Tab.MAP;rebuild();});button("返回",left+318,20,54,this::onClose);
        if(!supported() && !flag(state,"available"))return;
        if(tab==Tab.LOBBY){
            JsonObject room=mine();
            if(room==null){
                button("难度："+difficulty.title+" / "+difficulty.waves+" 波",left,top+18,175,()->{difficulty=ChallengeRules.Difficulty.values()[(difficulty.ordinal()+1)%ChallengeRules.Difficulty.values().length];rebuild();});
                button("创建房间",left+185,top+18,90,()->action("create",difficulty.name()));
                int y=top+65;for(JsonElement e:array("rooms")){JsonObject r=e.getAsJsonObject();String id=text(r,"id");Button b=Button.builder(Component.literal(flag(r,"invited")?"接受邀请":"仅限邀请"),it->action("join",id)).bounds(left+280,y,90,18).build();b.active=flag(r,"invited") && (text(r,"phase").equals("LOBBY")||text(r,"phase").equals("BUILDING"));addRenderableWidget(b);y+=30;}
            }else{
                boolean host=text(room,"host").equals(text(state,"self"));String phase=text(room,"phase");
                button("离开房间",left+275,top+18,95,()->minecraft.setScreen(new ConfirmScreen(yes->{if(yes)action("leave","");minecraft.setScreen(this);},Component.literal("离开挑战？"),Component.literal("开局后离场将放弃本局通关奖励，原背包会恢复。")){public boolean isPauseScreen(){return false;}}));
                if(host && phase.equals("LOBBY")){
                    button("开始（支持单人）",left,top+45,150,()->action("start",""));
                    button("难度："+ChallengeRules.Difficulty.parse(text(room,"difficulty")).title,left+160,top+45,110,()->action("difficulty",ChallengeRules.Difficulty.values()[(ChallengeRules.Difficulty.parse(text(room,"difficulty")).ordinal()+1)%ChallengeRules.Difficulty.values().length].name()));
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
                JsonObject offer=array("shop").get(i).getAsJsonObject();String order=text(offer,"id")+":"+number(state,"shopRevision")+":"+number(offer,"cost");
                Button b=Button.builder(Component.literal("兑换 · "+number(offer,"cost")+" 币"),it->action("buy",order)).bounds(left+245,top+30+(i-start)*27,125,18).build();
                b.active=!flag(state,"locked")&&number(state,"coins")>=number(offer,"cost");addRenderableWidget(b);
            }
            pages(array("shop").size(),count);
        }else if(tab==Tab.LOADOUT){
            if(!flag(state,"locked")){
                button("主1：免费MP5",left,top+35,118,()->action("primary","-1"));button("主2：空槽",left+126,top+35,118,()->action("primary2","-1"));button("副：免费格洛克",left+252,top+35,118,()->action("secondary","-1"));
                int count=Math.max(1,(height-top-130)/30),start=page*count;
                for(int i=start;i<Math.min(array("weapons").size(),start+count);i++){
                    JsonObject w=array("weapons").get(i).getAsJsonObject();String slot=Integer.toString(number(w,"slot"));int y=top+77+(i-start)*30;
                    String[] roles={"primary","primary2","secondary"};for(int role=0;role<3;role++){String key=roles[role];var b=Button.builder(Component.literal(number(state,key)==number(w,"slot")?"已选":role==2?"手枪":"主"+(role+1)),it->action(key,slot)).bounds(left+224+role*49,y,45,18).build();b.active=(role==2)==flag(w,"pistol");addRenderableWidget(b);}
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
            if(r==null){g.drawString(font,"封锁研究所 · 三层 / "+ChallengeArena.ROOMS.size()+" 间房 · 每队 1–4 人",left,top,0xFFE6EBEF);g.drawString(font,"房间列表（由房主邀请后加入）",left,top+48,0xFFA1AAB6);int y=top+67;for(JsonElement e:array("rooms")){JsonObject q=e.getAsJsonObject();g.drawString(font,font.plainSubstrByWidth(text(q,"name")+" · "+ChallengeRules.Difficulty.parse(text(q,"difficulty")).title+" · "+number(q,"count")+"/4",265),left,y,0xFFE6EBEF);g.drawString(font,phase(text(q,"phase")),left,y+11,0xFFA1AAB6);y+=30;}}
            else {g.drawString(font,"房间 "+text(r,"id")+" · "+phase(text(r,"phase")),left,top,0xFF8AF0A8);g.drawString(font,"队伍 "+number(r,"count")+"/4 · "+ChallengeRules.Difficulty.parse(text(r,"difficulty")).title,left,top+23,0xFFE6EBEF);
                if(!text(r,"phase").equals("LOBBY")&&!text(r,"phase").equals("BUILDING")){
                    g.drawString(font,"波次 "+number(r,"wave")+"/"+number(r,"total")+" · 本波剩余 "+number(r,"remaining")+" · "+number(r,"seconds")+" 秒",left,top+55,0xFFE6EBEF);
                    g.drawString(font,ChallengeClient.batchStatus(r),left,top+68,0xFFE1BA7C);
                    g.drawWordWrap(font,Component.literal(text(r,"phase").equals("RUNNING")?"按B打开战术购买轮盘；购买不扣结算分。\n整波所有敌人清空才换波，Boss存活不断增援。":"全员加载后准备30秒。柜旁按住R 3秒补弹，松开取消。"),left,top+84,360,0xFFA1AAB6);
                }
                else g.drawString(font,"房主邀请在线玩家：",left,top+78,0xFFA1AAB6);
            }
        }else if(tab==Tab.TASKS){
            g.drawString(font,"通关 "+number(state,"wins")+" 次 · 击杀 "+number(state,"kills")+" · 最高分 "+number(state,"best"),left,top,0xFFE6EBEF);
            String[] rewards={"兑换币 +4","兑换币 +6","兑换币 +12"};
            for(int i=0;i<array("tasks").size();i++){JsonObject t=array("tasks").get(i).getAsJsonObject();g.drawString(font,text(t,"title"),left,top+22+i*36,flag(t,"ready")?0xFF8AF0A8:0xFFE6EBEF);g.drawString(font,rewards[i],left,top+34+i*36,0xFFA1AAB6);}
            g.drawString(font,font.plainSubstrByWidth(text(state,"last"),365),left,top+126,0xFFE1BA7C);
            g.drawWordWrap(font,Component.literal("战斗积分与兑换币独立。每 "+number(state,"exchangeRate")+" 分结算 1 兑换币，余数保留；任务直接给币。当前兑换币："+number(state,"coins")),left,top+168,360,0xFFA1AAB6);
        }else if(tab==Tab.SHOP){
            g.drawString(font,"兑换币："+number(state,"coins")+" · 本局："+number(state,"earned")+" 分 / 可兑 "+number(state,"exchangePreview")+" 币",left,top,0xFF8AF0A8);
            int count=Math.max(1,(height-top-80)/27),start=page*count;
            for(int i=start;i<Math.min(array("shop").size(),start+count);i++)g.drawString(font,text(array("shop").get(i).getAsJsonObject(),"title"),left,top+35+(i-start)*27,0xFFE6EBEF);
            if(flag(state,"locked"))g.drawString(font,"结束后按 "+number(state,"exchangeRate")+" 分 : 1 币结算；阵亡也结算。",left,top+14,0xFFE1BA7C);
            else g.drawString(font,"枪械：配方估值 + 25% 装配费，再折算兑换币",left,top+14,0xFFA1AAB6);
        }else if(tab==Tab.LOADOUT){
            g.drawString(font,"主1："+selectedName("primary")+" · 主2："+selectedName("primary2")+" · 副："+selectedName("secondary"),left,top,0xFF8AF0A8);
            g.drawString(font,"本次携枪费 "+number(state,"carryFee")+" 兑换币 · 成功开局扣费 · 按战斗力估价",left,top+15,0xFFE1BA7C);
            if(flag(state,"locked"))g.drawString(font,"本局已锁定装备，结束后再选择。",left,top+28,0xFFE1BA7C);
            else{
                int count=Math.max(1,(height-top-130)/30),start=page*count;
                for(int i=start;i<Math.min(array("weapons").size(),start+count);i++){
                    JsonObject w=array("weapons").get(i).getAsJsonObject();int y=top+77+(i-start)*30;
                    var stack=ChallengeClient.weaponIcon(number(w,"slot"));
                    if(!stack.isEmpty())g.renderItem(stack,left,y+1);
                    g.drawString(font,font.plainSubstrByWidth(text(w,"name"),195),left+22,y+1,0xFFE6EBEF);
                    g.drawString(font,"入场 "+number(w,"fee")+" 币",left+22,y+13,0xFFE1BA7C);
                }
            }
        }else{
            String text="研究所：81×81 / 三层 / "+ChallengeArena.ROOMS.size()+" 间大小房间\n一层：零号封锁门、接待后勤，主尸群入口\n二层：中央实验翼、环形绕行通道、观察病房\n三层：西侧通道、横向机房、隔离翼与维护厅\n\n北侧单梯上二层，绕中央翼至西侧单梯上三层。\n破损房间可向下跳一层，下方黄色标记缓冲区。\n一层大量出怪，第8波开放二层，第15波开放三层。\n零号封锁门每波激活，红灯爆闪预告零散入口。\n三层可连续跳落，沿走廊到一层唯一弹药柜。\n5格内按住R 3秒补弹，松开取消，冷却10秒。\n饥饿锁定；两瓶治疗II；最多两主武器＋一手枪。\n普通僵尸慢速压场；幼尸、苦力怕、猪人构成威胁。\nBoss波铁傀儡→装配傀儡→坚守者，存活时不断增援。\n极限25波，大Boss与小Boss同时出现。\nB分类轮盘：独立战术点购买，不扣结算分。\n每批10–20只动态施压，上限96只/房间。";
            g.drawWordWrap(font,Component.literal(text),left,top,365,0xFFE6EBEF);
        }
        String notice=text(state,"notice");if(!notice.isEmpty())g.drawString(font,font.plainSubstrByWidth(notice,370),left,height-16,0xFFE1BA7C);
        if(tab==Tab.LOBBY&&mine()!=null&&mine().has("sites")&&!mine().getAsJsonArray("sites").isEmpty())g.drawWordWrap(font,Component.literal(ChallengeClient.activeSites()),left,top+125,365,0xFFFF8D8D);
        super.render(g,mx,my,dt);
    }
    private String selectedName(String key){int slot=state.has(key)?number(state,key):-1;for(JsonElement e:array("weapons")){JsonObject w=e.getAsJsonObject();if(number(w,"slot")==slot)return font.plainSubstrByWidth(text(w,"name"),62);}return slot<0?(key.equals("primary")?"MP5A5":key.equals("primary2")?"空":"格洛克17"):"槽 "+(slot+1);}
    private static String phase(String phase){return switch(phase){case "BUILDING"->"地图生成中";case "LOBBY"->"等待开局";case "LOADING"->"等待地图加载";case "COUNTDOWN"->"首波准备倒计时";case "RUNNING"->"战斗中";case "REST"->"波间补给";default->phase;};}
}
