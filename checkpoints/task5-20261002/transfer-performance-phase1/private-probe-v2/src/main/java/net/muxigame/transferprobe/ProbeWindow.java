package net.muxigame.transferprobe;

import java.util.HashSet;
import java.util.Set;

/** Bounded diagnostics only. This class never changes game or protection state. */
public final class ProbeWindow {
    public static final long LIMIT_NANOS = 120_000_000_000L;
    public final String transfer;
    public final String source;
    public final String target;
    public final long started;
    public int teleportId = -1;
    public int chunkX;
    public int chunkZ;
    public boolean positionKnown;
    private int remaining = 256;
    private final java.util.Map<String,Integer> noisyCounts=new java.util.HashMap<>();
    private final Set<String> seen = new HashSet<>();

    public ProbeWindow(String transfer, String source, String target, long started) {
        this.transfer = transfer;
        this.source = source;
        this.target = target;
        this.started = started;
    }

    public synchronized boolean take(long now, String event, boolean once) {
        if (now - started < 0 || now - started >= LIMIT_NANOS) return false;
        if ((event.equals("chunk_batch_send") || event.equals("chunk_batch_receive") || event.equals("chunk_queue_sample")) && noisyCounts.merge(event,1,Integer::sum)>8) return false;
        // Preserve final once-only phase markers even after noisy batch sampling runs out.
        if (remaining == 0 && (!once || seen.size() >= 32)) return false;
        if (once && !seen.add(event)) return false;
        if (remaining > 0) remaining--;
        return true;
    }

    public static boolean maySkipExtraReload(boolean enabled, boolean dimensionCaller,
                                            String source, String target) {
        // Experimental A/B only, limited to the three current Core worlds.
        // Login, logout, other mods' worlds and explicit reloads remain untouched.
        return enabled && dimensionCaller && source != null && target != null
                && !source.equals(target) && coreWorld(source) && coreWorld(target);
    }

    private static boolean coreWorld(String id) {
        return id.equals("minecraft:overworld") || id.equals("muxi_game_core:overworld")
                || id.equals("muxi_game_core:adventure");
    }

    public static boolean dimensionReloadFrame(String owner, String method) {
        return owner.equals("net.minecraft.client.Minecraft")
                && method.contains("euphoria_patcher") && method.contains("lambda$onDimensionChange");
    }
}
