package com.euphoriapatches.euphoria_patcher.integration.iris;
import net.muxigame.transferprobe.ProbeWindow;
/** Counter replacing shader work, not a measurement of GL shader compilation. */
public final class IrisReloadManager {
    public static boolean guarded;
    public static String source;
    public static String target;
    public static int requested;
    public static int executed;
    public static void findAndScheduleReload() {
        requested++;
        if (!ProbeWindow.maySkipExtraReload(guarded, true, source, target)) executed++;
    }
}
