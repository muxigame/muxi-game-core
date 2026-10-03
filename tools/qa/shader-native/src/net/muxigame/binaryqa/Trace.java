package net.muxigame.binaryqa;
import java.util.*;
/** Per-process, per-case bounded inclusive spans. Does not retain event/world objects. */
public final class Trace {
 public static volatile boolean ready; private static long epoch,dropped,loginNs,resetNs;
 private record Start(long epoch,long ns){}
 private static final ThreadLocal<Map<String,Deque<Start>>> stack=ThreadLocal.withInitial(HashMap::new);
 private static final Map<String,long[]> totals=new TreeMap<>();private static final List<Map<String,Object>> spans=new ArrayList<>();
 public static synchronized void reset(){ready=false;epoch++;resetNs=System.nanoTime();totals.clear();spans.clear();dropped=loginNs=0;stack.remove();ready=true;}
 public static synchronized void begin(String n){if(!ready)return;long now=System.nanoTime();if(n.equals("clientLogin"))loginNs=now;stack.get().computeIfAbsent(n,k->new ArrayDeque<>()).push(new Start(epoch,now));}
 public static synchronized void end(String n){var q=stack.get().get(n);if(q==null||q.isEmpty())return;var a=q.pop();if(a.epoch==epoch)add(n,a.ns,System.nanoTime());}
 public static synchronized void add(String n,long start,long end){if(!ready||start<resetNs)return;var a=totals.computeIfAbsent(n,k->new long[3]);a[0]++;a[1]+=end-start;a[2]=Math.max(a[2],end-start);if(end-start>=100_000L||n.equals("pipelineCreated")){if(spans.size()<16384)spans.add(Map.of("stage",n,"beginNs",start,"endNs",end,"inclusiveMs",(end-start)/1e6,"thread",Thread.currentThread().getName()));else dropped++;}}
 public static synchronized long count(String n){var a=totals.get(n);return a==null?0:a[0];}
 public static synchronized Map<String,Object> snapshot(){var out=new TreeMap<String,Object>();totals.forEach((n,a)->out.put(n,Map.of("calls",a[0],"inclusiveMs",a[1]/1e6,"maxMs",a[2]/1e6)));return Map.of("totals",out,"spans",List.copyOf(spans),"dropped",dropped,"spanCap",16384,"retention","Only >=100us spans plus successful pipeline creation counters; aggregates count every call","clientLoginNs",loginNs,"clockDomain",ProcessHandle.current().pid(),"limits","Overlapping elapsed wall spans; not exclusive or GPU time. Never subtract timestamps from another process.");}
 public static String compileFamily(){return StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().startsWith("dev.djefrey.colorwheel.")))?"compile-colorwheel":"compile-iris-mojang";}
}
