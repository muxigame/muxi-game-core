package net.muxigame.shadernative;
import com.google.gson.*;
/** Bounded scalar active-join observations; no world, UI, future or exception retention. */
public final class JoinObservation {
 private static long generation,active,sequence;private static int dropped;private static String lastUi,lastLogging;private static JsonArray events=new JsonArray();
 public static synchronized void begin(String route){generation++;active=generation;sequence=0;dropped=0;lastUi=lastLogging=null;events=new JsonArray();event(active,"joinObservationBegin",route);}
 public static synchronized long token(){return active;}
 public static synchronized void event(long token,String kind,String detail){if(token==0||token!=active)return;if(events.size()>=64){dropped++;return;}var row=new JsonObject();row.addProperty("clientNanoTime",System.nanoTime());row.addProperty("thread",Thread.currentThread().getName());row.addProperty("kind",kind);row.addProperty("detail",detail);events.add(row);}
 public static synchronized void ui(String screen,String overlay){String next="screen="+screen+";overlay="+overlay;if(!next.equals(lastUi)){lastUi=next;event(active,"uiChanged",next);}}
 public static synchronized void logging(JsonObject state,String boundary,boolean force){String next=state.toString();if(force||!next.equals(lastLogging)){lastLogging=next;event(active,"logging@"+boundary,next);}}
 public static synchronized long reloadBegin(long token){if(token==0||token!=active)return 0;long id=++sequence;event(token,"nativeResourceReloadBegin",Long.toString(id));return id;}
 public static void reloadEnd(long token,long id,Throwable failure){if(id!=0)event(token,"nativeResourceReloadComplete",id+":"+(failure==null?"success":failure.getClass().getName()));}
 public static synchronized JsonObject finish(){var out=snapshot();active=0;return out;}
 public static synchronized JsonObject snapshot(){var out=new JsonObject();out.addProperty("scope","ConnectScreen start through qualified first visible frame only; excludes startup and routes");out.addProperty("clockDomain",ProcessHandle.current().pid());out.addProperty("eventCap",64);out.addProperty("dropped",dropped);out.add("events",events.deepCopy());return out;}
 private JoinObservation(){}
}
