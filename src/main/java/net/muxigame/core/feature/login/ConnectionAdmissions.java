package net.muxigame.core.feature.login;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Single-use admission evidence for one exact connection object, measured by a monotonic clock. */
public final class ConnectionAdmissions<C> {
    private final Map<C,Long> expires=new IdentityHashMap<>();
    private final LongSupplier clock;
    private final long ttl;
    public ConnectionAdmissions(long ttlNanos,LongSupplier clock){
        if(ttlNanos<=0) throw new IllegalArgumentException("Admission TTL must be positive");
        this.ttl=ttlNanos;this.clock=clock;
    }
    public synchronized void record(C connection){
        if(connection==null) throw new IllegalArgumentException("Missing connection");
        long now=clock.getAsLong();expires.values().removeIf(value->value<=now);
        expires.put(connection,now+ttl);
    }
    public synchronized boolean consume(C connection){
        Long deadline=expires.remove(connection);return deadline!=null && deadline>clock.getAsLong();
    }
    public synchronized void discard(C connection){expires.remove(connection);}
    public synchronized void clear(){expires.clear();}
}
