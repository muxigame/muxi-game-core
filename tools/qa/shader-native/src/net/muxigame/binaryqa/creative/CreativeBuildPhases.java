package net.muxigame.binaryqa.creative;

import java.util.ArrayDeque;
import net.muxigame.binaryqa.Trace;

/** Per-invocation metadata only: strings/nanoseconds, never event, registry, or world. */
public final class CreativeBuildPhases {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = new ThreadLocal<>();
    private CreativeBuildPhases() {}
    public static final class Frame {
        final String tab;
        final boolean recording;
        long start;
        String phase = "collectionPrepare";
        Frame(String tab) { this.tab=tab; recording=Trace.ready; start=System.nanoTime(); }
    }
    public static Frame enter(String tab) {
        ArrayDeque<Frame> frames=FRAMES.get();
        if(frames==null) { frames=new ArrayDeque<>(); FRAMES.set(frames); }
        Frame frame=new Frame(tab); frames.push(frame); return frame;
    }
    public static void boundary(String nextPhase) {
        ArrayDeque<Frame> frames=FRAMES.get();
        if(frames==null || frames.isEmpty()) return;
        Frame frame=frames.peek();
        long now=System.nanoTime();
        emit(frame,now,false);
        frame.phase=nextPhase;
        // Exclude Trace.add synchronization/diagnostic overhead from next interval.
        frame.start=System.nanoTime();
    }
    public static void leave(Frame frame,boolean completed) {
        ArrayDeque<Frame> frames=FRAMES.get();
        if(frames==null) return;
        try { if(frames.peek()==frame) emit(frame,System.nanoTime(),!completed); }
        finally {
            if(frames.peek()==frame) frames.pop(); else frames.remove(frame);
            if(frames.isEmpty()) FRAMES.remove();
        }
    }
    private static void emit(Frame frame,long end,boolean aborted) {
        if(!frame.recording || !Trace.ready) return;
        try {
            Trace.add("creativePhase/"+frame.phase+(aborted?"/aborted":"")+"@"+frame.tab,frame.start,end);
        } catch(RuntimeException | LinkageError diagnosticFailure) {
            // Diagnostic failures do not replace the native call result/exception.
        }
    }
}
