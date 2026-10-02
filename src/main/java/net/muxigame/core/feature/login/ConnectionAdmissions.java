package net.muxigame.core.feature.login;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Single-use admission evidence for one exact connection object, measured by a monotonic clock. */
public final class ConnectionAdmissions<C> {
    private record Admission(long deadline,Object identity) {}
    private final Map<C,Admission> expires=new IdentityHashMap<>();
    private final LongSupplier clock;
    private final long ttl;
    public ConnectionAdmissions(long ttlNanos,LongSupplier clock){
        if(ttlNanos<=0) throw new IllegalArgumentException("Admission TTL must be positive");
        this.ttl=ttlNanos;this.clock=clock;
    }
    public synchronized void record(C connection){record(connection,null);}
    public synchronized void record(C connection,Object identity){
        if(connection==null) throw new IllegalArgumentException("Missing connection");
        long now=clock.getAsLong();expires.values().removeIf(value->value.deadline()<=now);
        expires.put(connection,new Admission(now+ttl,identity));
    }
    public synchronized boolean consume(C connection){return consume(connection,null);}
    public synchronized boolean consume(C connection,Object identity){
        Admission proof=expires.remove(connection);
        return proof!=null && proof.deadline()>clock.getAsLong() && java.util.Objects.equals(proof.identity(),identity);
    }
    public synchronized void discard(C connection){expires.remove(connection);}
    public synchronized void clear(){expires.clear();}
}
