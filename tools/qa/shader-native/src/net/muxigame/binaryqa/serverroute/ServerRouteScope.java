package net.muxigame.binaryqa.serverroute;

import net.muxigame.binaryqa.Trace;
import net.muxigame.shadernative.ServerTimeline;

/** Only primitive route scope; no server, world, future, packet or event is retained. */
public final class ServerRouteScope {
    private static final ThreadLocal<Long> ROUTE = new ThreadLocal<>();
    private ServerRouteScope() {}
    public static Long enter(long token) { Long previous=ROUTE.get();ROUTE.set(token);return previous; }
    public static void leave(Long previous) {if(previous==null)ROUTE.remove();else ROUTE.set(previous);}
    public static long start() {
        if(!Trace.ready)return 0;
        Long route=ROUTE.get();
        return route!=null&&route!=0&&route==ServerTimeline.token()?System.nanoTime():0;
    }
    public static void finish(String stage,long begin) {finish(stage,begin,Integer.MIN_VALUE,Integer.MIN_VALUE);}
    public static void finish(String stage,long begin,int x,int z) {
        if(begin==0)return;long end=System.nanoTime();Long route=ROUTE.get();
        if(!Trace.ready||route==null||route==0||route!=ServerTimeline.token())return;
        Trace.add(stage,begin,end);
        ServerTimeline.event(route,stage,x,Integer.MIN_VALUE,z,end-begin);
    }
}
