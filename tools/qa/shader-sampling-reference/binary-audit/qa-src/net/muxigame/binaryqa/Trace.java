package net.muxigame.binaryqa;
import java.util.*;
public final class Trace {
 public static volatile boolean ready;
 private static final Map<String,long[]> totals=new TreeMap<>();
 private static final ThreadLocal<Map<String,Deque<Long>>> stack=ThreadLocal.withInitial(HashMap::new);
 private static final List<Map<String,Object>> spans=new ArrayList<>();
 private static long loginNs,respawnNs;
 public static void begin(String name){if(!ready)return;long now=System.nanoTime();if(name.equals("clientLogin"))loginNs=now;if(name.equals("clientRespawn"))respawnNs=now;stack.get().computeIfAbsent(name,k->new ArrayDeque<>()).push(now);}
 public static void end(String name){if(!ready)return;var values=stack.get().get(name);if(values==null||values.isEmpty())return;add(name,values.pop(),System.nanoTime());}
 public static synchronized void add(String name,long begin,long end){if(!ready)return;var value=totals.computeIfAbsent(name,k->new long[3]);value[0]++;value[1]+=end-begin;value[2]=Math.max(value[2],end-begin);if(spans.size()<8192)spans.add(Map.of("stage",name,"beginNs",begin,"endNs",end,"durationMs",(end-begin)/1e6));}
 public static synchronized Map<String,Object> snapshot(){var values=new TreeMap<String,Object>();totals.forEach((key,v)->values.put(key,Map.of("calls",v[0],"ms",v[1]/1e6,"maxMs",v[2]/1e6)));return Map.of("totals",values,"loginBeginNs",loginNs,"respawnBeginNs",respawnNs,"nowNs",System.nanoTime());}
 public static synchronized List<Map<String,Object>> spans(){return List.copyOf(spans);}
 public static String compileFamily(){return StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().startsWith("dev.djefrey.colorwheel.")))?"compile-colorwheel":"compile-iris-mojang";}
}
