package net.muxigame.inputlinkqa;
import com.google.gson.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class ProbeCounters {
    public static final AtomicInteger gun=new AtomicInteger(),sent=new AtomicInteger(),received=new AtomicInteger(),swap=new AtomicInteger();
    public static volatile JsonObject frame=new JsonObject();
}
