package net.muxigame.core.client.tasks;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Client-local display preferences; never influence progress or rewards. */
final class TaskHudSettings {
    boolean visible=true;
    int left=12;
    int width=190;
    double topFraction=0.30;
    private final Path file;
    TaskHudSettings(Path game) {
        file=game.resolve("config/muxi-daily-tasks-client.json");
        try {
            if(!Files.isRegularFile(file) || Files.size(file)>8192) return;
            JsonObject o=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(o.has("visible")) visible=o.get("visible").getAsBoolean();
            if(o.has("left")) left=Math.max(4,Math.min(200,o.get("left").getAsInt()));
            if(o.has("width")) width=Math.max(120,Math.min(280,o.get("width").getAsInt()));
            if(o.has("topFraction")) {
                double n=o.get("topFraction").getAsDouble(); if(Double.isFinite(n)) topFraction=Math.max(0,Math.min(0.8,n));
            }
        } catch(Exception ignored) { /* A bad cosmetic setting must never prevent login. */ }
    }
    void save() {
        try {
            JsonObject o=new JsonObject(); o.addProperty("visible",visible); o.addProperty("left",left);
            o.addProperty("width",width); o.addProperty("topFraction",topFraction);
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
