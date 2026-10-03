package net.muxigame.shadernative;
import java.util.*;
public final class ServerTimeline {
 private static long serial,token;private static String dimension="";private static final Map<String,Long> hits=new TreeMap<>();private static final List<Map<String,Object>> events=new ArrayList<>();private static long dropped;
 public static synchronized void begin(String d){token=++serial;dimension=d;hits.clear();events.clear();dropped=0;}
 public static synchronized long token(){return token;}
 public static synchronized boolean dimensionMatches(String d){return token!=0&&dimension.equals(d);}
 public static synchronized void mark(String n){event(token,n,0,0,0,0);}
 public static synchronized void event(long t,String n,int x,int y,int z,long ns){if(token==0||token!=t)return;hits.merge(n,1L,Long::sum);if(events.size()<2048)events.add(Map.of("stage",n,"nowNs",System.nanoTime(),"thread",Thread.currentThread().getName(),"x",x,"y",y,"z",z,"inclusiveNs",ns));else dropped++;}
 public static synchronized Map<String,Object> snapshot(){return Map.of("hits",new TreeMap<>(hits),"events",List.copyOf(events),"dropped",dropped,"clockDomain",ProcessHandle.current().pid());}
 public static synchronized void end(){token=0;}
}
