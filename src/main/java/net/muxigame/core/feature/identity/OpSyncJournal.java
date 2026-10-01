package net.muxigame.core.feature.identity;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Server-thread-only consumption ledger. Repeated website snapshots never override in-game edits. */
public final class OpSyncJournal {
    public record Desired(String uid, UUID uuid, long revision, int level) {
        public Desired {
            if (!IdentityRules.validUid(uid) || !IdentityRules.offlineUuid(uid).equals(uuid)
                || revision < 1 || level < 0 || level > 4) throw new IllegalArgumentException("Invalid OP identity/version");
        }
    }
    public interface Target {
        void setLevel(Desired desired) throws IOException;
        int level(Desired desired);
    }
    private final Path path;
    private final Map<String, Desired> applied = new TreeMap<>();
    private final Map<String, Desired> pending = new TreeMap<>();
    public OpSyncJournal(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            if (!Files.isRegularFile(path) || Files.size(path) > 16 * 1024 * 1024) throw new IOException("Invalid OP journal");
            try {
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!root.get("schema").getAsJsonPrimitive().isNumber() || !root.get("schema").getAsString().matches("[12]"))
                    throw new IllegalArgumentException();
                int schema = root.get("schema").getAsInt();
                if (schema != 1 && schema != 2) throw new IllegalArgumentException();
                if (!root.keySet().equals(schema==1?Set.of("schema","applied"):Set.of("schema","applied","pending")))
                    throw new IllegalArgumentException();
                for (JsonElement entry : root.getAsJsonArray("applied")) {
                    Desired d = parse(entry.getAsJsonObject());
                    if (applied.put(d.uid(), d) != null) throw new IllegalArgumentException();
                }
                if (schema == 2) for (JsonElement entry : root.getAsJsonArray("pending")) {
                    Desired d = parse(entry.getAsJsonObject());
                    Desired old = applied.get(d.uid());
                    if (pending.put(d.uid(), d) != null || old != null && d.revision() <= old.revision())
                        throw new IllegalArgumentException();
                }
            } catch (RuntimeException error) { throw new IOException("Invalid OP journal"); }
        }
    }
    public static Desired parse(JsonObject row) {
        if (!row.keySet().equals(Set.of("uid", "offlineUuid", "revision", "level"))) throw new IllegalArgumentException();
        if (!row.get("uid").isJsonPrimitive() || !row.get("uid").getAsJsonPrimitive().isString()
            || !row.get("revision").getAsJsonPrimitive().isNumber() || !row.get("revision").getAsString().matches("[1-9][0-9]*")
            || !row.get("level").getAsJsonPrimitive().isNumber() || !row.get("level").getAsString().matches("[0-4]")) throw new IllegalArgumentException();
        String uuid = row.get("offlineUuid").getAsString();
        UUID parsed = UUID.fromString(uuid);
        if (!parsed.toString().equals(uuid)) throw new IllegalArgumentException();
        return new Desired(row.get("uid").getAsString(), parsed,
                           Long.parseLong(row.get("revision").getAsString()), row.get("level").getAsInt());
    }
    public static JsonObject json(Desired d) {
        JsonObject o = new JsonObject();
        o.addProperty("uid", d.uid()); o.addProperty("offlineUuid", d.uuid().toString());
        o.addProperty("revision", d.revision()); o.addProperty("level", d.level());
        return o;
    }
    public int consume(Desired next, Target target) throws IOException {
        Desired unfinished = pending.get(next.uid());
        if (unfinished != null) {
            if (next.revision() < unfinished.revision() || next.revision() == unfinished.revision() && !next.equals(unfinished))
                throw new IOException("Stale or conflicting pending OP version");
            complete(unfinished, target);
        }
        Desired old = applied.get(next.uid());
        if (old != null && (next.revision() < old.revision() || next.revision() == old.revision() && !next.equals(old)))
            throw new IOException("Stale or conflicting OP version");
        if (old == null || next.revision() > old.revision()) {
            Map<String, Desired> processing = new TreeMap<>(pending); processing.put(next.uid(), next);
            save(applied, processing); // Write-ahead record must be durable before touching native ops.
            pending.clear(); pending.putAll(processing);
            complete(next, target);
        }
        return target.level(next); // Report in-game edits; do not apply the old website level again.
    }
    public boolean hasPending() { return !pending.isEmpty(); }

    /** Local OP commands are blocked until all write-ahead operations have completed. */
    public void recover(Target target) throws IOException {
        for (Desired d : List.copyOf(pending.values())) complete(d, target);
    }

    private void complete(Desired next, Target target) throws IOException {
        // A crash after native save but before the commit marker needs only the marker on restart.
        if (target.level(next) != next.level()) target.setLevel(next);
        if (target.level(next) != next.level()) throw new IOException("Native OP save did not apply");
        Map<String, Desired> updated = new TreeMap<>(applied); updated.put(next.uid(), next);
        Map<String, Desired> processing = new TreeMap<>(pending); processing.remove(next.uid());
        save(updated, processing);
        applied.clear(); applied.putAll(updated);
        pending.clear(); pending.putAll(processing);
    }

    private void save(Map<String, Desired> values, Map<String, Desired> processing) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        JsonObject root = new JsonObject(); root.addProperty("schema", 2);
        JsonArray entries = new JsonArray(); values.values().forEach(v -> entries.add(json(v))); root.add("applied", entries);
        JsonArray unfinished = new JsonArray(); processing.values().forEach(v -> unfinished.add(json(v))); root.add("pending", unfinished);
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "op-journal-", ".tmp");
        try {
            try (FileChannel file = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(root.toString());
                while (data.hasRemaining()) file.write(data);
                file.force(true);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
