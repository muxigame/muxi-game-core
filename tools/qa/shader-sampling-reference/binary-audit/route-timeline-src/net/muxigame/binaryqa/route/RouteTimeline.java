package net.muxigame.binaryqa.route;

import java.util.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Private QA only. Scalar snapshots; no retained world, packet, section or event. */
public final class RouteTimeline {
    private static final int CAP = 2048;
    private static long serial;
    private static volatile State current;
    private static final class State {
        final long generation, beginNs = System.nanoTime();
        final String route, dimension;
        final boolean dynamicTarget;
        final List<Map<String,Object>> events = new ArrayList<>();
        final Map<String,Long> hits = new TreeMap<>();
        final Map<String,Long> firstNs = new TreeMap<>(), lastNs = new TreeMap<>(), elapsedTotalNs = new TreeMap<>(), elapsedMaxNs = new TreeMap<>();
        final Map<String,Integer> retained = new HashMap<>();
        long dropped, stale, endNs, frames, compiledFrame;
        String reason;
        int x,y,z; boolean resolved, loadedSeen, compiledSeen; volatile boolean closed;
        State(String r,String d,double x,double y,double z) {
            generation=++serial; route=r;dimension=d;
            dynamicTarget=!(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z));
            if(!dynamicTarget)resolve(x,y,z);
        }
        void resolve(double a,double b,double c){x=(int)Math.floor(a);y=(int)Math.floor(b);z=(int)Math.floor(c);resolved=true;}
    }
    private RouteTimeline(){}
    public static synchronized void begin(String route,String targetDim,double x,double y,double z) {
        current=new State(Objects.requireNonNull(route),Objects.requireNonNull(targetDim),x,y,z);
        mark("routeBegin");
    }
    public static long token(){State s=current;return s==null||s.closed?0:s.generation;}
    public static synchronized void mark(String name){event(token(),name,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,0);}
    public static synchronized void event(long token,String stage,int x,int y,int z,long elapsedNs){
        State s=current;if(s==null||s.closed||token==0)return;
        if(token!=s.generation){s.stale++;return;}
        s.hits.merge(stage,1L,Long::sum);
        long now=System.nanoTime();s.firstNs.putIfAbsent(stage,now);s.lastNs.put(stage,now);
        if(elapsedNs>0){s.elapsedTotalNs.merge(stage,elapsedNs,Long::sum);s.elapsedMaxNs.merge(stage,elapsedNs,Math::max);}
        if(x!=Integer.MIN_VALUE&&s.resolved&&(Math.abs((long)x-(s.x>>4))>2||Math.abs((long)z-(s.z>>4))>2))return;
        boolean landing=s.resolved&&x==(s.x>>4)&&z==(s.z>>4);
        if(landing){String key=stage+"@landingColumn";s.hits.merge(key,1L,Long::sum);s.firstNs.putIfAbsent(key,now);s.lastNs.put(key,now);
            if(y==(s.y>>4)){key=stage+"@landingSection";s.hits.merge(key,1L,Long::sum);s.firstNs.putIfAbsent(key,now);s.lastNs.put(key,now);}}
        boolean slow=elapsedNs>=5_000_000L;
        String bucket=stage+(landing?":landing":slow?":slow":":other");int limit=landing||slow?64:8;
        int count=s.retained.getOrDefault(bucket,0);if(count>=limit){s.dropped++;return;}s.retained.put(bucket,count+1);
        if(s.events.size()>=CAP){s.dropped++;return;}
        Map<String,Object> e=new LinkedHashMap<>(); e.put("stage",stage);e.put("nowNs",now);
        e.put("thread",Thread.currentThread().getName());e.put("threadId",Thread.currentThread().threadId());
        if(x!=Integer.MIN_VALUE){e.put("sectionX",x);e.put("sectionY",y);e.put("sectionZ",z);e.put("landingColumn",s.resolved&&x==(s.x>>4)&&z==(s.z>>4));e.put("landingSection",s.resolved&&x==(s.x>>4)&&y==(s.y>>4)&&z==(s.z>>4));}
        if(elapsedNs>0)e.put("inclusiveElapsedNs",elapsedNs);s.events.add(e);
    }
    public static boolean clientDimensionMatches(){State s=current;var mc=Minecraft.getInstance();return s!=null&&!s.closed&&mc.isSameThread()&&mc.level!=null&&s.dimension.equals(mc.level.dimension().location().toString());}
    public static synchronized boolean dimensionMatches(String d){State s=current;return s!=null&&!s.closed&&s.dimension.equals(d);}
    public static void packet(int x,int z,boolean end){
        long t=token();if(t==0)return;var mc=Minecraft.getInstance();
        if(!mc.isSameThread()){if(!end)event(t,"packetListenerOffClientThreadEntry",x,Integer.MIN_VALUE,z,0);return;}
        if(clientDimensionMatches())event(t,end?"packetApplyReturn":"packetApplyHead",x,Integer.MIN_VALUE,z,0);
    }
    public static synchronized void resolvePlayerTarget(){
        State s=current;if(s==null||s.closed||s.resolved||!clientDimensionMatches())return;
        var p=Minecraft.getInstance().player;if(p==null)return;
        s.resolve(p.getX(),p.getY(),p.getZ());mark("dynamicTargetResolved");
    }
    public static synchronized void frame(){
        State s=current;if(s==null||s.closed)return;s.frames++;
        if(!clientDimensionMatches())return;
        if(!s.resolved)return; // Dynamic join target is locked only after the position packet RETURN.
        if(s.compiledSeen)return;
        var mc=Minecraft.getInstance();var p=new BlockPos(s.x,s.y,s.z);
        boolean loaded=mc.level.hasChunkAt(p); // Public non-loading query.
        if(loaded&&!s.loadedSeen){s.loadedSeen=true;mark("firstFrameLandingChunkLoaded");}
        if(loaded&&mc.levelRenderer.isSectionCompiled(p)){s.compiledSeen=true;s.compiledFrame=s.frames;mark("firstRenderLevelReturnLandingSectionCompiled");}
    }
    public static synchronized void end(String reason){State s=current;if(s==null||s.closed)return;mark("routeEnd");s.reason=reason;s.endNs=System.nanoTime();s.closed=true;}
    public static synchronized Map<String,Object> snapshot(){
        State s=current;if(s==null)return Map.of();Map<String,Object> o=new LinkedHashMap<>();
        o.put("route",s.route);o.put("generation",s.generation);o.put("dimension",s.dimension);o.put("beginNs",s.beginNs);o.put("endNs",s.endNs);o.put("reason",s.reason);
        o.put("dynamicTarget",s.dynamicTarget);o.put("targetResolved",s.resolved);o.put("targetXYZ",List.of(s.x,s.y,s.z));o.put("hits",new TreeMap<>(s.hits));o.put("droppedEvents",s.dropped);o.put("staleTokenEventsRejected",s.stale);o.put("recordCap",CAP);o.put("frames",s.frames);o.put("compiledFrame",s.compiledFrame);o.put("events",new ArrayList<>(s.events));
        o.put("stageFirstNs",new TreeMap<>(s.firstNs));o.put("stageLastNs",new TreeMap<>(s.lastNs));o.put("inclusiveElapsedTotalNs",new TreeMap<>(s.elapsedTotalNs));o.put("inclusiveElapsedMaxNs",new TreeMap<>(s.elapsedMaxNs));o.put("sampling","First 64 events per stage in landing column, first 8 other nearby events; aggregates include all accepted hits even after sampling. Overlapping elapsed sums are not exclusive.");
        o.put("limitations","Listener entry is not socket receive; off-client-thread packet dimension is unknown. No force loading. Mesh creation is not guaranteed submission. Upload markers are CPU-side and batch spans include nested work; elapsed is neither exclusive nor GPU time. Compiled section at renderLevel RETURN is not pixel visibility or presentation. Missing RETURN may indicate cancellation/exception; zero hits are not proof of no work. Events cover target +/-2 chunk columns; hit counts include all matching-dimension calls. Pre-existing jobs have no route token.");return o;
    }
    public static void dump(String file)throws java.io.IOException{Files.writeString(Path.of(file),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot()));}
}
