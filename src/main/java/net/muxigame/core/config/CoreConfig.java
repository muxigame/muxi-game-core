package net.muxigame.core.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Private runtime configuration lives in the server, never in this repository. */
public record CoreConfig(Identity identity) {
    public static final String FILE = "config/muxi-game-core.json";
    public static final String DEFAULT_ENDPOINT = "https://account.muxigame.com/api/internal/minecraft/identity/";

    public record Identity(boolean enabled, String endpoint, String serverKey, int refreshSeconds) {
        @Override public String toString() {
            return "Identity[enabled=" + enabled + ", refreshSeconds=" + refreshSeconds + ", serverKey=<redacted>]";
        }
    }

    public static CoreConfig load(Path file) {
        try {
            if (!Files.exists(file)) return disabled();
            if (Files.size(file) > 65536) throw new IllegalArgumentException();
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // JSON parser messages may quote serverKey: never attach the cause.
            throw new IllegalStateException("Invalid " + FILE + "; check schema and enabled feature settings.");
        }
    }

    public static CoreConfig disabled() {
        return new CoreConfig(new Identity(false, DEFAULT_ENDPOINT, "", 60));
    }

    public static CoreConfig parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("schema") && (!root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber()
                || !"1".equals(root.get("schema").getAsString()))) throw new IllegalArgumentException();
            if (!root.has("features")) return disabled();
            JsonObject features = root.getAsJsonObject("features");
            if (!features.has("identity")) return disabled();
            JsonObject source = features.getAsJsonObject("identity");
            if (!source.has("enabled")) return disabled();
            if (!source.get("enabled").isJsonPrimitive() || !source.get("enabled").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
            if (!source.get("enabled").getAsBoolean()) return disabled();
            String endpoint = source.get("endpoint").getAsString();
            String key = source.get("serverKey").getAsString();
            if (source.has("refreshSeconds") && (!source.get("refreshSeconds").isJsonPrimitive()
                || !source.get("refreshSeconds").getAsJsonPrimitive().isNumber()
                || !source.get("refreshSeconds").getAsString().matches("[0-9]+"))) throw new IllegalArgumentException();
            int seconds = source.has("refreshSeconds") ? source.get("refreshSeconds").getAsInt() : 60;
            if (seconds < 10 || seconds > 600) throw new IllegalArgumentException();
            URI uri = URI.create(endpoint);
            boolean https = "https".equals(uri.getScheme());
            boolean loopback = "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
            if ((!https && !loopback) || uri.getHost() == null || !endpoint.endsWith("/")
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || key.length() < 32 || key.length() > 512 || key.chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException();
            return new CoreConfig(new Identity(true, endpoint, key, seconds));
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Invalid Game Core identity configuration (values redacted)");
        }
    }
}
