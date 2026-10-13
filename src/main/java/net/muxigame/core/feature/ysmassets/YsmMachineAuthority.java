package net.muxigame.core.feature.ysmassets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Explicit preapproved machine credentials only. No environment/key borrowing or credential generation. */
public final class YsmMachineAuthority {
    public static final long WINDOW_MS=30_000;
    public static final class Credential {
        private final String serverId,keyId;private final byte[] secret;private final Set<String> purposes,audiences;
        public Credential(String serverId,String keyId,byte[] existingApprovedSecret,Set<String> purposes,Set<String> audiences){
            field(serverId);field(keyId);if(existingApprovedSecret==null||existingApprovedSecret.length<32)throw new IllegalArgumentException("Invalid registered machine credential");
            this.serverId=serverId;this.keyId=keyId;secret=existingApprovedSecret.clone();this.purposes=Set.copyOf(purposes);this.audiences=Set.copyOf(audiences);this.purposes.forEach(YsmMachineAuthority::field);this.audiences.forEach(YsmMachineAuthority::field);
        }
        public String serverId(){return serverId;}public String keyId(){return keyId;}
        @Override public String toString(){return "Credential[serverId="+serverId+",keyId="+keyId+",secret=<redacted>]";}
    }
    public record Proof(String serverId,String keyId,String purpose,String audience,String timestamp,String nonce,String signature) {}
    public record Verified(String serverId,String purpose,String keyId) {}
    @FunctionalInterface public interface Credentials {Credential lookup(String keyId);}
    @FunctionalInterface public interface ReplayStore {boolean consume(String identity,String nonce,long expiresAt,long now);}
    /** Bounded volatile replay store for isolated tests. Production must explicitly provide its replay policy/store. */
    public static final class MemoryReplayStore implements ReplayStore {
        private final ConcurrentHashMap<String,Long> used=new ConcurrentHashMap<>();
        @Override public synchronized boolean consume(String identity,String nonce,long expires,long now){
            used.entrySet().removeIf(e->e.getValue()<now);if(used.size()>=4096)return false;return used.putIfAbsent(identity+":"+nonce,expires)==null;
        }
    }
    public static final class Unconfigured extends IllegalStateException {public Unconfigured(){super("YSM machine authority not configured");}}
    private final String audience;private final Credentials credentials;private final ReplayStore replay;private final LongSupplier clock;
    public YsmMachineAuthority(String audience,Credentials credentials,ReplayStore replay,LongSupplier clock){field(audience);this.audience=audience;this.credentials=credentials;this.replay=replay;this.clock=Objects.requireNonNull(clock);}
    static void field(String s){if(s==null||!s.matches("[A-Za-z0-9._:-]{1,128}"))throw denied();}
    static IllegalArgumentException denied(){return new IllegalArgumentException("Invalid YSM machine authority");}
    public static String bodyHash(byte[] body){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
    private static String material(Proof p,String method,String path,byte[] body){
        field(p.serverId());field(p.keyId());field(p.purpose());field(p.audience());
        if(!p.timestamp().matches("0|[1-9][0-9]{0,18}")||!p.nonce().matches("[a-f0-9]{32}")||!method.equals("POST")||!path.matches("/[A-Za-z0-9_/-]{1,160}")||body.length>16384)throw denied();
        return String.join("\n","muxi-ysm-machine-v1",p.serverId(),p.keyId(),p.purpose(),p.audience(),p.timestamp(),p.nonce(),method,path,bodyHash(body));
    }
    private static byte[] mac(Credential c,String text){try{var m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(c.secret,"HmacSHA256"));return m.doFinal(text.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Machine signing unavailable");}}
    public static Proof sign(Credential credential,String purpose,String audience,String method,String path,byte[] body,long now,String nonce){
        if(!credential.purposes.contains(purpose)||!credential.audiences.contains(audience))throw denied();
        var p=new Proof(credential.serverId,credential.keyId,purpose,audience,Long.toString(now),nonce,"");return new Proof(p.serverId(),p.keyId(),p.purpose(),p.audience(),p.timestamp(),p.nonce(),HexFormat.of().formatHex(mac(credential,material(p,method,path,body))));
    }
    public boolean configured(){return credentials!=null&&replay!=null;}
    public Verified verify(Proof proof,String expectedPurpose,String method,String path,byte[] body){
        if(credentials==null||replay==null)throw new Unconfigured();if(proof==null)throw denied();
        String message=material(proof,method,path,body);var c=credentials.lookup(proof.keyId());
        if(c==null||!c.keyId.equals(proof.keyId())||!c.serverId.equals(proof.serverId())||!c.purposes.contains(expectedPurpose)||!expectedPurpose.equals(proof.purpose())||!audience.equals(proof.audience())||!c.audiences.contains(audience)||!proof.signature().matches("[a-f0-9]{64}"))throw denied();
        long now=clock.getAsLong(),time;try{time=Long.parseLong(proof.timestamp());}catch(NumberFormatException e){throw denied();}
        if(time<now-WINDOW_MS||time>now+WINDOW_MS||!MessageDigest.isEqual(mac(c,message),HexFormat.of().parseHex(proof.signature())))throw denied();
        if(!replay.consume(audience+":"+c.serverId+":"+c.keyId,proof.nonce(),time+2*WINDOW_MS,now))throw denied();return new Verified(c.serverId,expectedPurpose,c.keyId);
    }
    public static Map<String,String> headers(Proof p){
        return Map.of("X-Muxi-Ysm-Server",p.serverId(),"X-Muxi-Ysm-Key",p.keyId(),"X-Muxi-Ysm-Purpose",p.purpose(),"X-Muxi-Ysm-Audience",p.audience(),"X-Muxi-Ysm-Time",p.timestamp(),"X-Muxi-Ysm-Nonce",p.nonce(),"X-Muxi-Ysm-Signature",p.signature());
    }
}
