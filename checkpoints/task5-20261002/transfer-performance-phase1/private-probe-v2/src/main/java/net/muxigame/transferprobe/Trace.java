package net.muxigame.transferprobe;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.game.*;
import org.slf4j.Logger;

public final class Trace {
    public static final boolean ENABLED = Boolean.getBoolean("muxi.transferProbe");
    public static final boolean SKIP_EXTRA_RELOAD = Boolean.getBoolean("muxi.transferProbe.skipExtraDimensionReload");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SESSION = UUID.randomUUID().toString();
    private static final long ORIGIN = System.nanoTime();
    private static final AtomicLong IDS = new AtomicLong();
    private static final Map<Object, ProbeWindow> SERVER = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile ProbeWindow client;

    public static ProbeWindow begin(String side, Object connection, String source, String target) {
        if (!ENABLED) return null;
        ProbeWindow window = new ProbeWindow(Long.toString(IDS.incrementAndGet()), source, target, System.nanoTime());
        if (side.equals("client")) client = window;
        else SERVER.put(connection, window);
        event(side, window, side.equals("client") ? "respawn_receive" : "request", false, null, null);
        return window;
    }

    public static ProbeWindow client() { return client; }
    public static ProbeWindow server(Object connection) { return SERVER.get(connection); }
    public static void client(String event) { event("client", client, event, true, null, null); }
    public static void event(String side, ProbeWindow window, String name, boolean once, String key, Object value) {
        if (!ENABLED || window == null) return;
        long now = System.nanoTime();
        if (!window.take(now, name, once)) return;
        JsonObject record = new JsonObject();
        record.addProperty("session", SESSION);
        record.addProperty("transfer", window.transfer);
        record.addProperty("side", side);
        record.addProperty("event", name);
        record.addProperty("mono_ms", (now - ORIGIN) / 1_000_000.0);
        record.addProperty("source", window.source);
        record.addProperty("target", window.target);
        record.addProperty("teleport_id", window.teleportId);
        if (key != null) {
            if (value instanceof JsonElement json) record.add(key, json);
            else if (value instanceof Number number) record.addProperty(key, number);
            else if (value instanceof Boolean bool) record.addProperty(key, bool);
            else record.addProperty(key, String.valueOf(value));
        }
        LOGGER.info("MUXI_TRANSFER_TRACE {}", record);
    }

    public static void packet(Object connection, Packet<?> packet) {
        ProbeWindow w = server(connection);
        if (w == null) return;
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> child : bundle.subPackets()) packet(connection, child);
        } else if (packet instanceof ClientboundRespawnPacket) event("server", w, "respawn_send", true, null, null);
        else if (packet instanceof ClientboundPlayerPositionPacket position) {
            w.teleportId = position.getId();
            w.chunkX = ((int)Math.floor(position.getX())) >> 4;
            w.chunkZ = ((int)Math.floor(position.getZ())) >> 4;
            w.positionKnown = true;
            event("server", w, "position_send", true, null, null);
        } else if (packet instanceof ClientboundGameEventPacket gameEvent
                && gameEvent.getEvent() == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START) {
            event("server", w, "load_start_send", true, null, null);
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk && w.positionKnown
                && chunk.getX() == w.chunkX && chunk.getZ() == w.chunkZ) {
            event("server", w, "landing_chunk_send", true, null, null);
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            event("server", w, "chunk_batch_send", false, null, null);
        }
    }

    public static boolean dimensionReloadCaller() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                ProbeWindow.dimensionReloadFrame(frame.getClassName(), frame.getMethodName())));
    }

    private Trace() {}
}
