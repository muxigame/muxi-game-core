package net.muxigame.core.client;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Windows process-local capability. Only the launcher retains account refresh tokens. */
public final class TerminalCredentialBrokerClient {
    private static final Object SERIAL = new Object();
    private TerminalCredentialBrokerClient() {}
    public static boolean valid(String pipe, String secret) {
        return pipe != null && pipe.matches("muxi-terminal-[0-9a-f]{32}")
            && secret != null && secret.matches("[A-Za-z0-9_-]{43}");
    }
    public static CompletableFuture<String> fetch(String pipe, String secret) {
        if (!valid(pipe, secret)) return CompletableFuture.completedFuture("");
        final long deadline=System.nanoTime()+6_000_000_000L;
        return CompletableFuture.supplyAsync(() -> {
            // Serialize this game's native checks and exchange, including the server's pipe recreation gap.
            synchronized(SERIAL){
                while(System.nanoTime()<deadline){
                    try (RandomAccessFile channel = new RandomAccessFile("\\\\.\\pipe\\" + pipe, "rw")) {
                        channel.write((secret + "\n").getBytes(StandardCharsets.US_ASCII));
                        StringBuilder response = new StringBuilder();
                        for (int i = 0; i < 44; i++) {
                            int value = channel.read();
                            if (value == 10) {
                                String credential = response.toString();
                                return credential.matches("[A-Za-z0-9_-]{43}") ? credential : "";
                            }
                            if (value < 0 || value > 127) return "";
                            response.append((char) value);
                        }
                        return "";
                    }catch(java.io.IOException ignored){
                        try{Thread.sleep(25);}catch(InterruptedException stopped){Thread.currentThread().interrupt();return "";}
                    }
                }
            }
            return "";
        }).completeOnTimeout("", 7, TimeUnit.SECONDS).exceptionally(error -> "");
    }
}
