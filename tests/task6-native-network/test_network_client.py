"""Run the actual native-map client and drawing helper with deterministic client/Xaero/graphics doubles."""
from pathlib import Path
import subprocess,tempfile,json,os
from test_network_runtime import files,root
files=dict(files)
files.update({
'net/minecraft/util/FormattedCharSequence.java':'''package net.minecraft.util;public record FormattedCharSequence(String text){}''',
'net/minecraft/client/gui/Font.java':'''package net.minecraft.client.gui;import java.util.*;import net.minecraft.network.chat.Component;import net.minecraft.util.FormattedCharSequence;public class Font{public int lineHeight=9;public List<FormattedCharSequence> split(Component c,int w){List<FormattedCharSequence>lines=new ArrayList<>();String text=c.getString();for(int i=0;i<text.length();i+=w/6)lines.add(new FormattedCharSequence(text.substring(i,Math.min(text.length(),i+w/6))));return lines;}public int width(FormattedCharSequence s){return s.text().length()*6;}}''',
'com/mojang/blaze3d/vertex/PoseStack.java':'''package com.mojang.blaze3d.vertex;public class PoseStack{public int depth;public void pushPose(){depth++;}public void popPose(){depth--;}public void translate(double x,double y,double z){}public void scale(float x,float y,float z){}}''',
'net/minecraft/client/gui/GuiGraphics.java':'''package net.minecraft.client.gui;import java.util.*;import net.minecraft.util.FormattedCharSequence;import com.mojang.blaze3d.vertex.PoseStack;public class GuiGraphics{public final List<String> texts=new ArrayList<>();public final List<Integer> colors=new ArrayList<>();public final PoseStack pose=new PoseStack();public PoseStack pose(){return pose;}public void fill(int a,int b,int c,int d,int color){colors.add(color);}public void drawString(Font f,FormattedCharSequence s,int x,int y,int color,boolean shadow){texts.add(s.text());}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;public class Minecraft{public static final Minecraft MC=new Minecraft();public net.minecraft.server.level.ServerPlayer player;public net.minecraft.server.level.ServerLevel level;public Object connection;public final net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();public static Minecraft getInstance(){return MC;}public Object getConnection(){return connection;}}''',
'xaero/map/world/MapWorld.java':'''package xaero.map.world;public class MapWorld{public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>getCustomDimensionId(){return dimension;}}''',
'xaero/map/MapProcessor.java':'''package xaero.map;public class MapProcessor{public final xaero.map.world.MapWorld world=new xaero.map.world.MapWorld();public xaero.map.world.MapWorld getMapWorld(){return world;}}''',
'xaero/map/WorldMapSession.java':'''package xaero.map;public class WorldMapSession{public static WorldMapSession current=new WorldMapSession();public final MapProcessor processor=new MapProcessor();public static WorldMapSession getCurrentSession(){return current;}public boolean isUsable(){return true;}public MapProcessor getMapProcessor(){return processor;}}''',
'xaero/map/mods/gui/Waypoint.java':'''package xaero.map.mods.gui;import net.minecraft.resources.*;public class Waypoint{public String name;public ResourceLocation origin=ResourceLocation.tryParse("waystones:marker");public int x,y,z;public Waypoint(String n,int a,int b,int c){name=n;x=a;y=b;z=c;}public String getName(){return name;}public ResourceLocation getThirdPartyOrigin(){return origin;}public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}}''',
'xaero/map/mods/gui/WaypointRenderer.java':'''package xaero.map.mods.gui;public interface WaypointRenderer{WaypointRenderContext getContext();}''',
'xaero/map/mods/gui/WaypointRenderContext.java':'''package xaero.map.mods.gui;public class WaypointRenderContext{public float worldmapWaypointsScale=1;}''',
'xaero/map/element/render/ElementRenderInfo.java':'''package xaero.map.element.render;public class ElementRenderInfo{}''',
'xaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider.java':'''package xaero.map.graphics.renderer.multitexture;public class MultiTextureRenderTypeRendererProvider{}''',
'net/minecraft/client/renderer/MultiBufferSource.java':'''package net.minecraft.client.renderer;public class MultiBufferSource{public static class BufferSource{}}''',
'xaero/map/gui/GuiMap.java':'''package xaero.map.gui;public class GuiMap{}''',
'net/minecraft/client/gui/screens/Screen.java':'''package net.minecraft.client.gui.screens;public class Screen{}''',
'xaero/map/gui/IRightClickableElement.java':'''package xaero.map.gui;public interface IRightClickableElement{}''',
'xaero/map/gui/dropdown/rightclick/RightClickOption.java':'''package xaero.map.gui.dropdown.rightclick;public abstract class RightClickOption{public final String key;public boolean active=true;public RightClickOption(String label,int index,xaero.map.gui.IRightClickableElement element){key=label;}public RightClickOption setActive(boolean value){active=value;return this;}public abstract void onAction(net.minecraft.client.gui.screens.Screen screen);}''',
'xaero/map/mods/gui/WaypointReader.java':'''package xaero.map.mods.gui;public class WaypointReader{}''',
})
files['NetworkClientTest.java']='''import java.util.*;import java.lang.reflect.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.core.BlockPos;
import net.minecraft.server.*;import net.minecraft.server.level.*;
import net.muxigame.core.client.waystones.*;import net.muxigame.core.feature.waystones.*;
import net.neoforged.neoforge.network.PacketDistributor;import xaero.map.mods.gui.Waypoint;
 public class NetworkClientTest {
 static class Renderer extends net.muxigame.core.compat.mixin.xaeroworldmap.NetworkStoneRendererMixin implements xaero.map.mods.gui.WaypointRenderer {public xaero.map.mods.gui.WaypointRenderContext getContext(){return new xaero.map.mods.gui.WaypointRenderContext();}}
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static void receive(Object packet)throws Exception{Method method=WaystoneMapClient.class.getDeclaredMethod("receive",packet.getClass());method.setAccessible(true);method.invoke(null,packet);}
 static WaystoneMapNetwork.ViewRequest query(){return (WaystoneMapNetwork.ViewRequest)PacketDistributor.sent.getLast();}
 public static void main(String[] args)throws Exception{
  var mc=Minecraft.getInstance();var server=new MinecraftServer();var home=new ServerLevel("minecraft:overworld");var away=new ServerLevel("muxi_game_core:overworld");mc.level=home;mc.player=new ServerPlayer(server,home);mc.connection=new Object();
  var uid=UUID.randomUUID();var pos=new BlockPos(1,64,1);var node=new WaystoneMapNetwork.Node(uid,home.dimension().location(),pos,true);
  WaystoneMapClient.poll();var request=query();check(PacketDistributor.sent.size()==1,"opening native map requests metadata only");
  WaystoneMapClient.poll();check(PacketDistributor.sent.size()==1,"per-tick polling bounded");
  receive(new WaystoneMapNetwork.View(UUID.randomUUID(),home.dimension().location(),true,true,List.of(node)));check(!WaystoneMapClient.nearSource(),"unsolicited snapshot cannot populate state");
  var view=new WaystoneMapNetwork.View(request.request(),home.dimension().location(),true,false,List.of(node));receive(view);check(WaystoneMapClient.nearSource()&&WaystoneMapClient.portalLinked(home.dimension(),pos),"current matching server snapshot accepted");
  check(WaystoneMapClient.unavailableKey(away.dimension(),pos)!=null,"other dimensions do not invent target nodes");
  var remote=new WaystoneMapNetwork.Node(UUID.randomUUID(),away.dimension().location(),pos,false);receive(new WaystoneMapNetwork.View(request.request(),home.dimension().location(),true,false,List.of(node,remote)));
  check(WaystoneMapClient.unavailableKey(away.dimension(),pos).contains("cross_dimension_not_connected"),"cross-dimension network failure is explicit");
  xaero.map.WorldMapSession.current.processor.world.dimension=away.dimension();check(NativeStoneRenderer.viewDimension().equals(away.dimension()),"native dimension selector controls view");check(mc.level==home&&PacketDistributor.sent.size()==1,"dimension browsing does not move/fee/send a warp");
  xaero.map.WorldMapSession.current.processor.world.dimension=home.dimension();
  WaystoneMapClient.teleport(home.dimension(),pos);check(PacketDistributor.sent.getLast() instanceof WaystoneMapNetwork.Warp,"native map sends UUID warp, no arbitrary coordinates");var warp=(WaystoneMapNetwork.Warp)PacketDistributor.sent.getLast();check(warp.target().equals(uid),"actual server node UID used");
  int before=PacketDistributor.sent.size();WaystoneMapClient.teleport(home.dimension(),pos);check(PacketDistributor.sent.size()==before,"duplicate pending warp is blocked");
  receive(new WaystoneMapNetwork.Receipt(UUID.randomUUID(),uid,"completed","old result"));check(WaystoneMapClient.unavailableKey(home.dimension(),pos).contains("pending"),"unmatched async receipt is ignored");
  receive(new WaystoneMapNetwork.Receipt(warp.request(),uid,"pending","preparing"));check(WaystoneMapClient.unavailableKey(home.dimension(),pos).contains("pending"),"ACK keeps pending state");
  receive(new WaystoneMapNetwork.Receipt(warp.request(),uid,"completed","arrived"));check(WaystoneMapClient.unavailableKey(home.dimension(),pos)==null,"matching final receipt clears pending");
  String name="石碑真实全名和极长名称需要换行展示，不使用单字替代身份".repeat(4);var waypoint=new Waypoint(name,1,64,1);var g=new GuiGraphics();StoneLabelLayout.beginFrame();NativeStoneRenderer.draw(waypoint,true,1,100,100,g);
  check(String.join("",g.texts).startsWith(name),"actual full unicode name drawn without truncation");check(String.join("",g.texts).contains("portal_connected"),"hover states connected portal");check(g.colors.contains(0xffaa5bdd),"connected marker draws small portal badge");check(g.colors.contains(0xffa6b0ba),"actual stone silhouette drawn");check(g.pose.depth==0,"renderer restores pose stack");
  StoneLabelLayout.beginFrame();var first=StoneLabelLayout.place(0,0,100,20,false);var second=StoneLabelLayout.place(0,0,100,20,false);check(first!=null&&second!=null&&!first.intersects(second),"dense labels choose distinct placement");
  for(int i=0;i<20;i++)StoneLabelLayout.place(0,0,100,20,false);check(StoneLabelLayout.place(0,0,100,20,false)==null,"ordinary labels suppressed when no space");check(StoneLabelLayout.place(0,0,100,20,true)!=null,"focused full name always available");StoneLabelLayout.beginFrame();check(StoneLabelLayout.place(0,0,100,20,false).equals(first),"layout resets each native frame");
  var mixin=new Renderer();
  var method=Arrays.stream(net.muxigame.core.compat.mixin.xaeroworldmap.NetworkStoneRendererMixin.class.getDeclaredMethods()).filter(m->m.getName().equals("muxi$stone")).findFirst().get();method.setAccessible(true);
  var ci=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("test",true);method.invoke(mixin,waypoint,true,1d,1f,100d,100d,null,g,null,null,ci);check(ci.isCancelled()&&ci.getReturnValue(),"actual mixin hook replaces only native stone renderer");
  waypoint.origin=net.minecraft.resources.ResourceLocation.tryParse("user:marker");ci=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>("test",true);method.invoke(mixin,waypoint,false,1d,1f,100d,100d,null,g,null,null,ci);check(!ci.isCancelled(),"ordinary native waypoints unchanged");
  waypoint.origin=net.minecraft.resources.ResourceLocation.tryParse("waystones:marker");
  var menu=new net.muxigame.core.compat.mixin.xaeroworldmap.WaystoneWaypointMenuMixin(){};
  var options=new ArrayList<xaero.map.gui.dropdown.rightclick.RightClickOption>();for(int i=0;i<4;i++)options.add(new xaero.map.gui.dropdown.rightclick.RightClickOption("native",i,null){public void onAction(net.minecraft.client.gui.screens.Screen s){}});
  var menuCallback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ArrayList<xaero.map.gui.dropdown.rightclick.RightClickOption>>("test",true);menuCallback.setReturnValue(options);
  var menuMethod=Arrays.stream(net.muxigame.core.compat.mixin.xaeroworldmap.WaystoneWaypointMenuMixin.class.getDeclaredMethods()).filter(m->m.getName().equals("muxi$waystoneTeleportOption")).findFirst().get();menuMethod.setAccessible(true);menuMethod.invoke(menu,waypoint,null,menuCallback);
  check(options.get(3).key.equals("muxi.map.waystone.teleport")&&options.get(3).active,"native generic teleport option replaced by network action");check(options.getLast().key.contains("portal_connected")&&!options.getLast().active,"selection exposes connected badge status without inventing a portal node");
  var selectedCallback=new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<String>("test",true);selectedCallback.setReturnValue(name);var selectedMethod=Arrays.stream(net.muxigame.core.compat.mixin.xaeroworldmap.WaystoneWaypointMenuMixin.class.getDeclaredMethods()).filter(m->m.getName().equals("muxi$connectedSelection")).findFirst().get();selectedMethod.setAccessible(true);selectedMethod.invoke(menu,waypoint,selectedCallback);check(selectedCallback.getReturnValue().startsWith(name)&&selectedCallback.getReturnValue().contains("portal_connected"),"selection preserves complete real name and adds connected status");
  mc.player.tickCount+=61;check(!WaystoneMapClient.nearSource()&&!WaystoneMapClient.portalLinked(home.dimension(),pos),"metadata expires conservatively");
  WaystoneMapClient.poll();var old=query();mc.connection=new Object();receive(new WaystoneMapNetwork.View(old.request(),home.dimension().location(),true,true,List.of(node)));check(!WaystoneMapClient.nearSource(),"new connection rejects old snapshot even same dimension");
  WaystoneMapClient.poll();var fresh=query();receive(new WaystoneMapNetwork.View(fresh.request(),home.dimension().location(),true,true,List.of(node)));check(WaystoneMapClient.nearSource(),"fresh connection may receive fresh metadata");
  WaystoneMapClient.teleport(home.dimension(),pos);var oldWarp=(WaystoneMapNetwork.Warp)PacketDistributor.sent.getLast();mc.connection=new Object();int messages=mc.player.messages.size();receive(new WaystoneMapNetwork.Receipt(oldWarp.request(),uid,"completed","old arrival"));check(mc.player.messages.size()==messages,"old asynchronous warp receipt cannot affect a new connection");
  mc.level=null;check(!WaystoneMapClient.nearSource(),"disconnect clears marker state");
  mc.level=home;mc.connection=new Object();WaystoneMapClient.poll();var oldPlayerQuery=query();mc.player=new ServerPlayer(server,home);receive(new WaystoneMapNetwork.View(oldPlayerQuery.request(),home.dimension().location(),true,true,List.of(node)));check(!WaystoneMapClient.nearSource(),"respawned player on same connection rejects previous actor snapshot");
  System.out.println("{\\"success\\":true,\\"checks\\":"+checks+",\\"scope\\":\\"production map client/renderer/mixin handlers with synthetic graphics and Xaero; no native game launch\\"}");
 }
}'''

out=root/'evidence';out.mkdir(exist_ok=True)
aggregate=Path(os.environ.get('TASK6_COMPILER_DEPENDENCIES','C:/Users/Administrator/Documents/Codex/2026-10-01/task-21/music-app/build/compiler-dependencies.jar'))
with tempfile.TemporaryDirectory(dir=out) as temp:
 temp=Path(temp);sources=[]
 for name,text in files.items():
  p=temp/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(p)
 base=root/'muxi-game-core/src/main/java/net/muxigame/core'
 sources+=[base/'feature/waystones/WaystoneMapNetwork.java',*sorted((base/'feature/waystones/network').glob('*.java')),base/'compat/mixin/waystones/NetworkPendingTeleportMixin.java',*sorted((base/'client/waystones').glob('*.java')),base/'compat/mixin/xaeroworldmap/NetworkStoneRendererMixin.java',base/'compat/mixin/xaeroworldmap/NetworkMapFrameMixin.java',base/'compat/mixin/xaeroworldmap/WaystoneWaypointMenuMixin.java',base/'compat/mixin/xaeroworldmap/GuiMapAccessor.java']
 args=temp/'args';args.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in ['--release','21','-proc:none','-encoding','UTF-8','-cp',str(aggregate),'-d',str(temp/'classes'),*map(str,sources)]),encoding='utf-8')
 result=subprocess.run(['C:/Program Files/Java/jdk-24/bin/javac.exe','@'+str(args)],capture_output=True)
 if result.returncode:print(result.stderr.decode('utf-8','replace'));result.check_returncode()
 result=subprocess.run(['C:/Program Files/Java/jdk-24/bin/java.exe','-Dtest.config='+str(temp/'config'),'-cp',str(temp/'classes')+os.pathsep+str(aggregate),'NetworkClientTest'],capture_output=True)
 if result.returncode:print(result.stderr.decode('utf-8','replace'));result.check_returncode()
 report=json.loads(result.stdout);(out/'network-client-result.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report))
