import java.lang.reflect.*;
import net.muxigame.core.compat.logging.*;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.filter.*;

/** Real production lifecycle and Log4j objects; only LogBegone configuration is a fixture. */
public final class CreativeTraceLifecycleTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Field field(String name) throws Exception {
        Field f = CreativeTraceGate.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    static Object lease(LoggerContext context) throws Exception {
        Class<?> c = Class.forName("net.muxigame.core.compat.logging.CreativeTraceGate$Lease");
        Constructor<?> constructor = c.getDeclaredConstructor(LoggerContext.class);
        constructor.setAccessible(true); return constructor.newInstance(context);
    }
    static void installFixture(LoggerContext context) throws Exception {
        field("current").set(null, lease(context)); field("requested").setBoolean(null, true);
    }
    public static void main(String[] args) throws Exception {
        String previous = System.getProperty("muxi.creativeTraceGate");
        try (LoggerContext context = new LoggerContext("creative-lifecycle-test")) {
            context.start(new DefaultConfiguration());
            LoggerConfig root = context.getConfiguration().getRootLogger();
            Filter nativeFilter = mod.azure.logbegone.CommonMod.FILTER;
            root.addFilter(nativeFilter);
            System.setProperty("muxi.creativeTraceGate", "true");
            installFixture(context);
            check(root.getFilter() != nativeFilter, "real gate installed");
            Filter unrelated = new AbstractFilter() {};
            root.addFilter(unrelated);
            CreativeTraceGateServerEvents.onServerStopped(null);
            check(field("current").get(null) == null && !field("requested").getBoolean(null), "stop resets state");
            Filter[] survivors = ((CompositeFilter) root.getFilter()).getFiltersArray();
            check(survivors.length == 2 && survivors[0] == nativeFilter && survivors[1] == unrelated,
                "stop preserves native and subsequently added filters in order");
            CreativeTraceGateServerEvents.onServerStopped(null);
            check(root.getFilter() instanceof CompositeFilter, "stop is idempotent");
            root.removeFilter(unrelated);
            installFixture(context);
            System.setProperty("muxi.creativeTraceGate", "false");
            CreativeTraceGateServerEvents.onServerTick(null);
            check(root.getFilter() == nativeFilter && field("current").get(null) == null, "false tick releases gate");
            CreativeTraceGateServerEvents.onServerStarted(null);
            check(!field("requested").getBoolean(null), "disabled start stays disabled");
            // The fixture has deliberately different bytes: production pin verification must fail closed.
            System.setProperty("muxi.creativeTraceGate", "true");
            CreativeTraceGateServerEvents.onServerStarted(null);
            check(field("requested").getBoolean(null) && field("current").get(null) == null,
                "unsupported pin fails closed");
            check(root.getFilter() == nativeFilter, "failed attach preserves native chain");
            CreativeTraceGateServerEvents.onServerStopped(null);
            check(!field("requested").getBoolean(null), "failed installation reset on stop");
            CreativeTraceGateServerEvents.onServerStarted(null);
            check(field("requested").getBoolean(null), "next lifecycle attempts installation again");
            CreativeTraceGateServerEvents.onServerStopped(null);
            System.out.println("PASS: stop, idempotence, unrelated filter preservation, false tick, disabled start, pin fallback, restart");
        } finally {
            CreativeTraceGate.shutdown();
            if (previous == null) System.clearProperty("muxi.creativeTraceGate");
            else System.setProperty("muxi.creativeTraceGate", previous);
        }
    }
}
