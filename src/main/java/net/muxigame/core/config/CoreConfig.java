package net.muxigame.core.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Private runtime configuration lives in the server, never in this repository. */
public record CoreConfig(Identity identity, Login login, OpSync opSync, TerminalSso terminalSso) {
    public static final String FILE = "config/muxi-game-core.json";
    public static final String DEFAULT_ENDPOINT = "https://account.muxigame.com/api/internal/minecraft/identity/";
    public static final String DEFAULT_JOIN_ENDPOINT = "https://account.muxigame.com/api/internal/minecraft/join/";
    public static final String TERMINAL_SSO_ENDPOINT = "https://account.muxigame.com/api/internal/minecraft/";
    public CoreConfig(Identity identity, Login login, OpSync opSync) { this(identity,login,opSync,new TerminalSso(false,TERMINAL_SSO_ENDPOINT,"")); }
    /** Optional account handoff; independent of game admission, identity display and OP. */
    public record TerminalSso(boolean enabled,String endpoint,String serverKey) {
        @Override public String toString(){return "TerminalSso[enabled="+enabled+", serverKey=<redacted>]";}
    }

    public record Identity(boolean enabled, String endpoint, String serverKey, int refreshSeconds) {
        @Override public String toString() {
            return "Identity[enabled=" + enabled + ", refreshSeconds=" + refreshSeconds + ", serverKey=<redacted>]";
        }
    }

    /** Join-grant verification. Its own endpoint and key: a feature never borrows another's credentials. */
    public record Login(boolean enabled, String endpoint, String serverKey, int timeoutSeconds) {
        @Override public String toString() {
            return "Login[enabled=" + enabled + ", timeoutSeconds=" + timeoutSeconds + ", serverKey=<redacted>]";
        }
    }

    public record OpSync(boolean enabled, String endpoint, String serverKey) {
        @Override public String toString() { return "OpSync[enabled=" + enabled + ", serverKey=<redacted>]"; }
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
        return new CoreConfig(new Identity(false, DEFAULT_ENDPOINT, "", 60),
                              new Login(false, DEFAULT_JOIN_ENDPOINT, "", 6), new OpSync(false, "", ""));
    }

    public static CoreConfig parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("schema") && (!root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber()
                || !"1".equals(root.get("schema").getAsString()))) throw new IllegalArgumentException();
            if (!root.has("features")) return disabled();
            JsonObject features = root.getAsJsonObject("features");
            // 两节各自独立：只配 login 不配 identity 也要能用，反过来同理。
            // 早先这里是"没有 identity 就整份配置作废"，加第二个功能时必须拆开。
            Identity identity = identity(features);
            Login login = login(features);
            OpSync opSync = opSync(features);
            // A UID-shaped client profile alone is not authentication. Require the join gate.
            if (opSync.enabled() && (!identity.enabled() || !login.enabled())) throw new IllegalArgumentException();
            return new CoreConfig(identity, login, opSync, terminalSso(features));
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Invalid Game Core configuration (values redacted)");
        }
    }

    private static Identity identity(JsonObject features) {
        JsonObject source = section(features, "identity");
        if (source == null) return disabled().identity();
        String endpoint = source.get("endpoint").getAsString();
        String key = source.get("serverKey").getAsString();
        int seconds = boundedInt(source, "refreshSeconds", 60, 10, 600);
        checkEndpointAndKey(endpoint, key);
        return new Identity(true, endpoint, key, seconds);
    }

    private static OpSync opSync(JsonObject features) {
        JsonObject source = section(features, "opSync");
        if (source == null) return disabled().opSync();
        String endpoint = source.get("endpoint").getAsString();
        String key = source.get("serverKey").getAsString();
        checkEndpointAndKey(endpoint, key);
        if (!"/api/internal/game/ops-sync/".equals(URI.create(endpoint).getPath())) throw new IllegalArgumentException();
        return new OpSync(true, endpoint, key);
    }

    private static Login login(JsonObject features) {
        JsonObject source = section(features, "login");
        if (source == null) return disabled().login();
        String endpoint = source.get("endpoint").getAsString();
        String key = source.get("serverKey").getAsString();
        // 这个超时是玩家在登录界面上真实要等的时间，所以上限压得比刷新间隔紧得多。
        int seconds = boundedInt(source, "timeoutSeconds", 6, 2, 20);
        checkEndpointAndKey(endpoint, key);
        return new Login(true, endpoint, key, seconds);
    }
    private static TerminalSso terminalSso(JsonObject features) {
        JsonObject source=section(features,"terminalSso");
        if(source==null)return disabled().terminalSso();
        String endpoint=source.has("endpoint")?source.get("endpoint").getAsString():TERMINAL_SSO_ENDPOINT;
        String key=source.get("serverKey").getAsString();
        checkEndpointAndKey(endpoint,key);
        if(!TERMINAL_SSO_ENDPOINT.equals(endpoint))throw new IllegalArgumentException();
        return new TerminalSso(true,endpoint,key);
    }

    /** The section's object when it exists and is switched on, otherwise null. */
    private static JsonObject section(JsonObject features, String name) {
        if (!features.has(name)) return null;
        JsonObject source = features.getAsJsonObject(name);
        if (!source.has("enabled")) return null;
        if (!source.get("enabled").isJsonPrimitive()
            || !source.get("enabled").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
        return source.get("enabled").getAsBoolean() ? source : null;
    }

    private static int boundedInt(JsonObject source, String key, int fallback, int min, int max) {
        if (source.has(key) && (!source.get(key).isJsonPrimitive()
            || !source.get(key).getAsJsonPrimitive().isNumber()
            || !source.get(key).getAsString().matches("[0-9]+"))) throw new IllegalArgumentException();
        int value = source.has(key) ? source.get(key).getAsInt() : fallback;
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }

    private static void checkEndpointAndKey(String endpoint, String key) {
        URI uri = URI.create(endpoint);
        boolean https = "https".equals(uri.getScheme());
        boolean loopback = "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()));
        if ((!https && !loopback) || uri.getHost() == null || !endpoint.endsWith("/")
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || key.length() < 32 || key.length() > 512
            || key.chars().anyMatch(c -> c < 33 || c > 126)) throw new IllegalArgumentException();
    }
}
