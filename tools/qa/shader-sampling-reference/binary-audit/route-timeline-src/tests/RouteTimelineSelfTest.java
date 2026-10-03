package net.muxigame.binaryqa.route;
import java.util.*;
public final class RouteTimelineSelfTest {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  RouteTimeline.begin("one","minecraft:overworld",8.5,241,8.5);long old=RouteTimeline.token();
  for(int i=0;i<10000;i++)RouteTimeline.event(old,"meshBuildReturn",0,15,0,10);
  var a=RouteTimeline.snapshot();check(((List<?>)a.get("events")).size()<=65,"bounded records");
  check(((Map<?,?>)a.get("hits")).get("meshBuildReturn").equals(10000L),"counts survive sampling");
  check(a.get("droppedEvents").equals(9936L),"exact dropped count");
  RouteTimeline.end("done");RouteTimeline.mark("afterEnd");check(!((Map<?,?>)RouteTimeline.snapshot().get("hits")).containsKey("afterEnd"),"closed route ignored");
  RouteTimeline.begin("two","minecraft:overworld",Double.NaN,Double.NaN,Double.NaN);
  RouteTimeline.event(old,"oldWorkerReturn",0,15,0,1);var b=RouteTimeline.snapshot();
  check(b.get("dynamicTarget").equals(true)&&b.get("targetResolved").equals(false),"join target unresolved");
  check(b.get("staleTokenEventsRejected").equals(1L),"stale generation rejected");
  check(!((Map<?,?>)b.get("hits")).containsKey("meshBuildReturn"),"route stats reset");
  check(!((Map<?,?>)b.get("hits")).containsKey("oldWorkerReturn"),"stale event not attributed");
  System.out.println("PASS: bounded sampling, exact counts, closed route, dynamic target, per-route reset, stale worker rejection");
 }
}
