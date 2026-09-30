package net.muxigame.core.client.tasks;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Client-local display preferences; never influence progress or rewards. */
final class TaskHudSettings {
    boolean visible=true;
    boolean dailyExpanded=true;
    boolean mainlineExpanded=true;
    boolean challengeExpanded=true;
    int left=12;
    int width=114;
    final Set<String> trackedDaily=new LinkedHashSet<>();
    double topFraction=0.30;
    private final Path file;
    TaskHudSettings(Path game) {
        file=game.resolve("config/muxi-daily-tasks-client.json");
        try {
            if(!Files.isRegularFile(file) || Files.size(file)>8192) return;
            JsonObject o=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(o.has("visible")) visible=o.get("visible").getAsBoolean();
            if(o.has("dailyExpanded")) dailyExpanded=o.get("dailyExpanded").getAsBoolean();
            if(o.has("mainlineExpanded")) mainlineExpanded=o.get("mainlineExpanded").getAsBoolean();
            if(o.has("challengeExpanded")) challengeExpanded=o.get("challengeExpanded").getAsBoolean();
            if(o.has("left")) left=Math.max(4,Math.min(200,o.get("left").getAsInt()));
            if(o.has("width")) {
                int loaded=o.get("width").getAsInt();
                // 170 was the old default; migrate it to the slimmer tracker automatically.
                width=loaded==170?114:Math.max(96,Math.min(180,loaded));
            }
            if(o.has("trackedDaily") && o.get("trackedDaily").isJsonArray())
                for(JsonElement e:o.getAsJsonArray("trackedDaily")) if(e.isJsonPrimitive()) trackedDaily.add(e.getAsString());
            if(o.has("topFraction")) {
                double n=o.get("topFraction").getAsDouble(); if(Double.isFinite(n)) topFraction=Math.max(0,Math.min(0.8,n));
            }
        } catch(Exception ignored) { /* A bad cosmetic setting must never prevent login. */ }
    }
    boolean isTracked(String id) { return trackedDaily.contains(id); }
    void toggleTracked(String id) { if(!trackedDaily.remove(id)) trackedDaily.add(id); save(); }
    void pruneTracked(Collection<String> valid) { if(trackedDaily.removeIf(id->!valid.contains(id))) save(); }

    void save() {
        try {
            JsonObject o=new JsonObject(); o.addProperty("visible",visible);
            o.addProperty("dailyExpanded",dailyExpanded); o.addProperty("mainlineExpanded",mainlineExpanded);
            o.addProperty("challengeExpanded",challengeExpanded);
            o.addProperty("left",left);
            o.addProperty("width",width); o.addProperty("topFraction",topFraction);
            JsonArray tracked=new JsonArray(); trackedDaily.forEach(tracked::add); o.add("trackedDaily",tracked);
            Files.createDirectories(file.getParent());
            Path temp=Files.createTempFile(file.getParent(),"muxi-hud-",".tmp");
            try {
                Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(o)+"\n",StandardCharsets.UTF_8);
                try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
                catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch(Exception e) {
            org.slf4j.LoggerFactory.getLogger("muxi-game-core/tasks-client").warn("Could not save task HUD preferences");
        }
    }
}
