package net.muxigame.binaryqa;
import java.util.*;import net.minecraft.client.Minecraft;
public final class NativeProbe {
 private static volatile int targetX,targetZ;public static void target(int x,int z){targetX=x;targetZ=z;}
 private static final List<Map<String,Object>> records=new ArrayList<>();
 public static synchronized void reset(){records.clear();}
 public static synchronized void mark(String name){if(!Trace.ready||records.size()>=4096)return;var r=new LinkedHashMap<String,Object>();r.put("event",name);r.put("nowNs",System.nanoTime());r.put("thread",Thread.currentThread().getName());var mc=Minecraft.getInstance();if(mc.isSameThread()){r.put("screen",mc.screen==null?"none":mc.screen.getClass().getName());r.put("overlay",mc.getOverlay()==null?"none":mc.getOverlay().getClass().getName());if(mc.player!=null&&mc.level!=null){r.put("x",mc.player.getX());r.put("y",mc.player.getY());r.put("z",mc.player.getZ());r.put("hasPlayerChunk",mc.level.hasChunkAt(mc.player.blockPosition()));r.put("playerSectionCompiled",mc.levelRenderer.isSectionCompiled(mc.player.blockPosition()));}}records.add(r);}
 public static void packet(net.minecraft.network.protocol.Packet<?> p){if(p instanceof net.minecraft.network.protocol.BundlePacket<?> b){for(var child:b.subPackets())packet(child);}else if(p instanceof net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket c&&c.getX()==targetX&&c.getZ()==targetZ)mark("serverLandingChunkEnqueue");}
 public static synchronized void dump(String file)throws Exception{java.nio.file.Files.writeString(java.nio.file.Path.of(file),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("states",records,"spans",Trace.spans(),"limits","Private elapsed spans are nested; thread labels are retained; state/spans reset each transfer. No timing is exclusive GPU work.")));}
}
