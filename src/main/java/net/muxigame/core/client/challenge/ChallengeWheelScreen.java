package net.muxigame.core.client.challenge;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static net.muxigame.core.client.challenge.ChallengeClient.*;
import java.util.*;
/** Nested mouse-driven category wheel. Combat never pauses while buying. */
public final class ChallengeWheelScreen extends Screen {
    private static final List<String> CATEGORIES=List.of("手枪","冲锋枪","步枪","机枪","狙击枪","霰弹枪","护甲","爆炸物","补给");
    private String category="";private int slot,page;private boolean coins;private int tick;private String signature="";
    public ChallengeWheelScreen(){super(Component.literal("战术购买轮盘"));}
    public boolean isPauseScreen(){return false;}
    protected void init(){rebuild();}
    private List<JsonObject> offers(){return java.util.stream.StreamSupport.stream(array("battleShop").spliterator(),false).map(JsonElement::getAsJsonObject).filter(o->text(o,"category").equals(category)).toList();}
    private void button(String label,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,24).build());}
    private void rebuild(){
        clearWidgets();int cx=width/2,cy=height/2;signature=number(state,"battleRevision")+":"+number(state,"tactical")+":"+number(state,"coins");
        button("主武器1",cx-140,32,88,()->{slot=0;rebuild();});button("主武器2",cx-44,32,88,()->{slot=1;rebuild();});button("副武器·手枪",cx+52,32,88,()->{slot=2;rebuild();});
        button(category.isEmpty()?"关闭":"返回分类",cx-44,cy+14,88,()->{if(category.isEmpty())onClose();else{category="";page=0;rebuild();}});
        if(category.equals("补给"))button(coins?"用兑换币":"用战术点",cx-48,cy-18,96,()->{coins=!coins;rebuild();});
        var items=offers();int total=category.isEmpty()?CATEGORIES.size():Math.min(8,Math.max(0,items.size()-page*8));
        for(int i=0;i<total;i++){
            double angle=-Math.PI/2+i*2*Math.PI/Math.max(1,total);int x=cx+(int)(Math.cos(angle)*Math.min(155,width/2-80))-65,y=cy+(int)(Math.sin(angle)*Math.min(95,height/2-82))-12;
            if(category.isEmpty()){String c=CATEGORIES.get(i);button(c,x+13,y,104,()->{category=c;coins=false;page=0;rebuild();});}
            else{var offer=items.get(page*8+i);int cost=number(offer,coins?"coins":"cost");boolean pistol=category.equals("手枪"),weapon=!text(offer,"gun").isEmpty();
                var b=Button.builder(Component.literal(text(offer,"title")+" · "+cost+(coins?"币":"点")),it->action("battleBuy",text(offer,"id")+":"+slot+":"+number(state,"battleRevision")+":"+(coins?"coin":"tactical"))).bounds(x,y,130,24).build();
                b.active=cost>0&&number(state,coins?"coins":"tactical")>=cost&&(!weapon||(slot==2)==pistol);addRenderableWidget(b);
            }
        }
        if(!category.isEmpty()&&items.size()>8){button("上一组",cx-130,height-33,80,()->{page=Math.max(0,page-1);rebuild();});button("下一组",cx+50,height-33,80,()->{page=Math.min((items.size()-1)/8,page+1);rebuild();});}
    }
    public void tick(){if(++tick%20==0)action("list","");if(!signature.equals(number(state,"battleRevision")+":"+number(state,"tactical")+":"+number(state,"coins")))rebuild();if(!flag(state,"locked"))onClose();}
    public void renderBackground(GuiGraphics g,int mx,int my,float dt){}
    public void render(GuiGraphics g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xC0101720);g.drawCenteredString(font,"结算分 "+number(state,"earned")+"  ·  战术点 "+number(state,"tactical")+"  ·  兑换币 "+number(state,"coins"),width/2,12,0xFF8AF0A8);
        for(int angle=0;angle<360;angle+=3){double a=angle*Math.PI/180;int x=width/2+(int)(Math.cos(a)*Math.min(155,width/2-80)),y=height/2+(int)(Math.sin(a)*Math.min(95,height/2-82));g.fill(x,y,x+2,y+2,0x885DA5B6);}
        g.drawCenteredString(font,category.isEmpty()?"选择装备分类":category+" · 目标槽："+(slot==2?"手枪":"主武器"+(slot+1)),width/2,height/2-48,0xFFE6EBEF);
        g.drawCenteredString(font,"购买不扣结算分 · 临时装备离场不带出",width/2,height-50,0xFFE1BA7C);
        super.render(g,mx,my,dt);
        if(!category.isEmpty()){
            var items=offers();int total=Math.min(8,Math.max(0,items.size()-page*8));for(int i=0;i<total;i++){
                double angle=-Math.PI/2+i*2*Math.PI/Math.max(1,total);int x=width/2+(int)(Math.cos(angle)*Math.min(155,width/2-80))-65,y=height/2+(int)(Math.sin(angle)*Math.min(95,height/2-82))-12;
                var offer=items.get(page*8+i);if(!text(offer,"gun").isEmpty()){var registries=minecraft.level!=null?minecraft.level.registryAccess():net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);var icon=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(net.minecraft.resources.ResourceLocation.parse(text(offer,"gun"))).setCount(1).setAmmoCount(0).setAmmoInBarrel(false).setFireMode(com.tacz.guns.api.item.gun.FireMode.SEMI).build(registries);g.renderItem(icon,x+55,y-18);}
                else if(category.equals("补给"))g.renderItem(net.muxigame.core.feature.challenge.ChallengeBattleShop.potion(text(offer,"id")),x+55,y-18);
                else if(category.equals("护甲"))g.renderItem(new net.minecraft.world.item.ItemStack(text(offer,"id").equals("iron_armor")?net.minecraft.world.item.Items.IRON_CHESTPLATE:text(offer,"id").equals("diamond_armor")?net.minecraft.world.item.Items.DIAMOND_CHESTPLATE:net.minecraft.world.item.Items.NETHERITE_CHESTPLATE),x+55,y-18);
                if(mx>=x&&mx<x+130&&my>=y&&my<y+24&&!text(offer,"gun").isEmpty())g.renderTooltip(font,Component.literal("估算爆发DPS "+number(offer,"burstDps")+" / 持续 "+number(offer,"sustainedDps")+" · 弹匣 "+number(offer,"magazine")),mx,my);
            }
        }
    }
}
