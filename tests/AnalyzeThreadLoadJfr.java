import jdk.jfr.consumer.*;
import java.nio.file.Path;
import java.util.*;

/** Read-only, streaming JFR phase analysis; run with java AnalyzeThreadLoadJfr.java file fromMs toMs. */
class AnalyzeThreadLoadJfr {
    static void add(Map<String,Long> map,String name){map.merge(name,1L,Long::sum);}
    static void section(String title,Map<String,Long> counts,int limit) {
        System.out.println("\n"+title);
        counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .limit(limit).forEach(e->System.out.println(e.getValue()+"\t"+e.getKey()));
    }
    public static void main(String[] args)throws Exception {
        long from=Long.parseLong(args[1]),to=Long.parseLong(args[2]);
        Map<String,Long> threads=new HashMap<>(),mainFrames=new HashMap<>(),loaderFrames=new HashMap<>(),tickerFrames=new HashMap<>(),parks=new HashMap<>(),chunkPaths=new HashMap<>();
        try(var recording=new RecordingFile(Path.of(args[0]))) {
            while(recording.hasMoreEvents()) {
                var event=recording.readEvent();long stamp=event.getStartTime().toEpochMilli();
                if(stamp<from||stamp>=to)continue;
                String type=event.getEventType().getName();
                if(type.equals("jdk.ExecutionSample")||type.equals("jdk.NativeMethodSample")) {
                    var thread=event.getThread("sampledThread");if(thread==null)continue;
                    String name=thread.getJavaName();add(threads,name);
                    var stack=event.getStackTrace();if(stack==null)continue;
                    var frames=new LinkedHashSet<String>();
                    for(var frame:stack.getFrames()) {
                        var method=frame.getMethod();frames.add(method.getType().getName()+"."+method.getName());
                    }
                    if(name.equals("Server thread")) {
                        frames.forEach(f->add(mainFrames,f));
                        if(frames.contains("net.minecraft.server.level.ServerChunkCache.getChunk")) {
                            add(chunkPaths,"all ServerChunkCache.getChunk stacks");
                            if(frames.contains("net.minecraft.server.network.ServerGamePacketListenerImpl.handleMovePlayer"))add(chunkPaths,"player movement + getChunk");
                            if(frames.contains("net.minecraft.server.network.ServerGamePacketListenerImpl.handleMoveVehicle"))add(chunkPaths,"vehicle movement + getChunk");
                            if(frames.contains("net.minecraft.world.level.chunk.LevelChunk.postProcessGeneration"))add(chunkPaths,"postProcessGeneration + getChunk");
                            if(frames.stream().anyMatch(f->f.endsWith(".managedBlock")))add(chunkPaths,"getChunk + managedBlock (waiting/pumping tasks)");
                        }
                    }
                    if(name.startsWith("muxi-load-"))frames.stream().filter(f->!f.startsWith("java.")&&!f.startsWith("jdk.")&&
                        !f.startsWith("net.muxigame.")).findFirst().ifPresent(f->add(loaderFrames,f));
                    if(name.startsWith("muxi-server-tick-"))frames.stream().filter(f->!f.startsWith("java.")&&!f.startsWith("jdk.")&&
                        !f.startsWith("net.muxigame.")).findFirst().ifPresent(f->add(tickerFrames,f));
                } else if(type.equals("jdk.ThreadPark")&&event.getDuration().toMillis()>=100) {
                    var thread=event.getThread();var stack=event.getStackTrace();
                    if(thread!=null&&thread.getJavaName().equals("Server thread")&&stack!=null) {
                        StringBuilder trace=new StringBuilder();
                        stack.getFrames().stream().limit(8).forEach(frame->{
                            var method=frame.getMethod();trace.append(method.getType().getName()).append('.').append(method.getName()).append(" <- ");
                        });
                        add(parks,trace.toString());
                    }
                }
            }
        }
        System.out.println("Phase epoch milliseconds: "+from+" .. "+to);
        System.out.println("Execution/native sample counts are samples, not CPU time. Frame counts overlap.");
        section("Samples by thread",threads,40);
        section("Server-thread frames",mainFrames,30);
        section("Server-thread chunk call paths (overlapping sample counts)",chunkPaths,10);
        section("Dimension-loader top application frames",loaderFrames,20);
        section("Dimension-ticker top application frames",tickerFrames,30);
        section("Server-thread parks >= 100 ms",parks,10);
    }
}
