package net.muxigame.core.compat.logging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.Logging;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderControl;
import org.apache.logging.log4j.core.config.AppenderControlArraySet;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.message.ReusableParameterizedMessage;

/** Guarded removal of work for creative-phase TRACE that no current output accepts; explicit false restores native filtering. */
public final class CreativeTraceGate {
    private static final String PROPERTY = "muxi.creativeTraceGate";
    private static final String NATIVE_LOGGER = "net.neoforged.fml.ModContainer";
    private static final String BEFORE = "Firing event for phase {} for modid {} : {}";
    private static final String AFTER = "Fired event for phase {} for modid {} : {}";
    private static boolean requested;
    private static Lease current;
    private static volatile String unavailableReason = "off";
    private static final Map<String, String> PINS = Map.of(
        "org.apache.logging.log4j.core.config.LoggerConfig", "173232f7252c0818598ebd6e745946c1bfd95857dc4ae97da5e6c85e82a27221",
        "org.apache.logging.log4j.core.config.AppenderControl", "5f543973e9817ab590029fa1850762a1d27ad0b50d09650010c50fded1781164",
        "org.apache.logging.log4j.core.config.AppenderControlArraySet", "84c82ff4c3e2df287d863d33729417e51a68ed7816b08a4869dc52792ea7bff2",
        "org.apache.logging.log4j.core.filter.AbstractFilterable", "fa2bd0615a891bcd059bfa01c3e93b5a13657207cf304307a2a8135ebf187cf7",
        "org.apache.logging.log4j.core.filter.CompositeFilter", "6734504d301fa369af454974b654aecee0563d860efca6b4354f32ba4dafa27c",
        "mod.azure.logbegone.CommonMod", "fb3431055cc3709fba2dce4c76d988ebfca007badeb3fe6c06bef80893576626",
        "mod.azure.logbegone.JavaUtilLog4jFilter", "5e8f8ba99c750195969a2d1059e6d08f03f122566ca806bc5859de66dff4cef6",
        "net.neoforged.fml.ModContainer", "b21b0e39461ab25162e3963e2c7fe26a72b42dda193a8d26ec5dd8c0aa2d3e55",
        "net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent", "3743397b0be213f3e82cb6d3b377733bdf4b5bff542b1c081bd18a16fef009f7",
        "org.apache.logging.log4j.core.impl.Log4jLogEvent", "4cfe659f3799fcfa864b510ed355aa6e4568f18ddc1da2f8cb6cb51a3f55f482"
    );
    private static final Map<String, String> MESSAGE_PINS = Map.of(
        "org.apache.logging.log4j.message.ParameterizedMessage", "0333c2d463367ef1d9212495e8aace465b4f34645b1345be0cba09adf79db8c9",
        "org.apache.logging.log4j.message.ReusableParameterizedMessage", "04d03be8db97350942da98f0d6375fdc81e1b20c64c7d4f3a530977f330a4b94",
        "org.apache.logging.log4j.core.impl.MutableLogEvent", "b4ff83fdcaded31d0823a91f1df5eafc54ea4b8be8292c7cd7f4fa82aceecd61"
    );

    private CreativeTraceGate() {}

    private static boolean configured() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));
    }

    /** Called only by the subscriber for the current physical side. */
    public static void update() {
        boolean next = configured();
        if (current != null && !current.valid()) {
            current.close();
            current = null;
        }
        if (next == requested) return;
        requested = next;
        if (current != null) current.close();
        current = null;
        if (!next) { unavailableReason = "off"; return; }
        try {
            current = attach((LoggerContext) LogManager.getContext(false));
            unavailableReason = "active";
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError failure) {
            // Unsupported profiles keep the original logger and event behavior.
            unavailableReason = failure.getClass().getSimpleName();
        }
    }

    /** Releases this installation; a later server lifecycle may install again. */
    public static void shutdown() {
        try {
            if (current != null) current.close();
        } finally {
            current = null;
            requested = false;
            unavailableReason = "off";
        }
    }

    private static void verifyPins() throws ReflectiveOperationException, java.io.IOException {
        var pins = new ArrayList<>(PINS.entrySet());
        pins.addAll(MESSAGE_PINS.entrySet());
        for (var pin : pins) {
            Class<?> type = Class.forName(pin.getKey(), false, CreativeTraceGate.class.getClassLoader());
            try (InputStream input = type.getResourceAsStream("/" + pin.getKey().replace('.', '/') + ".class")) {
                if (input == null) throw new IllegalStateException("missing public class " + pin.getKey());
                String hash;
                try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())); }
                catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                if (!hash.equals(pin.getValue())) throw new IllegalStateException("unsupported public class " + pin.getKey());
            }
        }
    }

    private static Lease attach(LoggerContext context) throws ReflectiveOperationException, java.io.IOException {
        verifyPins();
        return new Lease(context);
    }

    private static List<String> strings(JsonArray array) {
        var result = new ArrayList<String>();
        if (array != null) for (var value : array) result.add(value.getAsString());
        return List.copyOf(result);
    }

    private static boolean equal(JsonArray array, List<String> snapshot) {
        if (array == null) return snapshot.isEmpty();
        if (array.size() != snapshot.size()) return false;
        for (int index = 0; index < array.size(); index++)
            if (!snapshot.get(index).equals(array.get(index).getAsString())) return false;
        return true;
    }

    private static Filter[] entries(Filter filter) {
        if (filter == null) return Filter.EMPTY_ARRAY;
        return filter.getClass() == CompositeFilter.class ? ((CompositeFilter) filter).getFiltersArray() : new Filter[]{filter};
    }

    private static Filter compose(List<Filter> filters) {
        if (filters.isEmpty()) return null;
        return filters.size() == 1 ? filters.getFirst() : CompositeFilter.createFilters(filters.toArray(Filter[]::new));
    }

    private static final class Lease implements AutoCloseable {
        final LoggerContext context;
        final Configuration configuration;
        final LoggerConfig root;
        final Filter logBegone;
        final JsonObject settings;
        final List<String> phrases, regex;
        final AppenderControlArraySet outputs;
        final Field level;
        final VarHandle filterField;
        final Gate gate;
        final PropertyChangeListener change;
        volatile boolean live = true;

        Lease(LoggerContext context) throws ReflectiveOperationException {
            this.context = context;
            configuration = context.getConfiguration();
            root = configuration.getLoggerConfig(NATIVE_LOGGER);
            if (root.getClass() != LoggerConfig.class || !root.getName().isEmpty() || root.getParent() != null)
                throw new IllegalStateException("unsupported logger hierarchy");
            Class<?> common = Class.forName("mod.azure.logbegone.CommonMod");
            logBegone = (Filter) common.getField("FILTER").get(null);
            settings = (JsonObject) common.getField("CONFIG").get(null);
            var values = settings.getAsJsonObject("logbegone");
            phrases = strings(values.getAsJsonArray("phrases"));
            regex = strings(values.getAsJsonArray("regex"));
            if (phrases.size() > 64 || regex.size() > 8
                || java.util.stream.Stream.concat(phrases.stream(), regex.stream()).mapToLong(String::length).sum() > 8192)
                throw new IllegalStateException("unsupported filter configuration size");
            for (String expression : regex) Pattern.compile(expression); // validation only, no pattern cache
            var outputField = LoggerConfig.class.getDeclaredField("appenders");
            outputField.setAccessible(true);
            outputs = (AppenderControlArraySet) outputField.get(root);
            if (outputs.getClass() != AppenderControlArraySet.class) throw new IllegalStateException("unsupported output set");
            level = AppenderControl.class.getDeclaredField("level");
            level.setAccessible(true);
            filterField = MethodHandles.privateLookupIn(AbstractFilterable.class, MethodHandles.lookup())
                .findVarHandle(AbstractFilterable.class, "filter", Filter.class);
            gate = new Gate(this);
            gate.start();
            change = event -> { if ("config".equals(event.getPropertyName())) live = false; };
            boolean installed = false;
            for (int attempt = 0; attempt < 16 && !installed; attempt++) {
                Filter before = root.getFilter();
                var next = new ArrayList<Filter>();
                next.add(gate);
                int nativeFilters = 0;
                for (Filter entry : entries(before)) {
                    if (entry instanceof Gate old && !old.owner.live) continue;
                    if (entry != logBegone) throw new IllegalStateException("unsupported native filter chain");
                    nativeFilters++;
                    next.add(entry);
                }
                if (nativeFilters < 1 || nativeFilters > 2) throw new IllegalStateException("unsupported LogBegone chain");
                installed = filterField.compareAndSet(root, before, compose(next));
            }
            if (!installed) throw new IllegalStateException("configuration changed during activation");
            context.addPropertyChangeListener(change);
        }

        boolean valid() { return live && context.getConfiguration() == configuration; }

        boolean rejectsTrace() throws IllegalAccessException {
            if (!valid() || !configured() || configuration.getLoggerConfig(NATIVE_LOGGER) != root || root.getParent() != null)
                return false;
            Filter chain = root.getFilter();
            boolean found = false;
            int nativeFilters = 0;
            for (Filter entry : entries(chain)) {
                if (entry == gate) found = true;
                else if (entry == logBegone) nativeFilters++;
                else return false;
            }
            if (!found || nativeFilters < 1 || nativeFilters > 2) return false;
            var values = settings.getAsJsonObject("logbegone");
            if (values == null || !equal(values.getAsJsonArray("phrases"), phrases) || !equal(values.getAsJsonArray("regex"), regex)) return false;
            AppenderControl[] snapshot = outputs.get();
            if (snapshot.length == 0 || snapshot.length > 16) return false;
            for (AppenderControl output : snapshot) {
                if (output.getClass() != AppenderControl.class || output.getFilter() != null) return false;
                Level threshold = (Level) level.get(output);
                if (threshold == null || threshold.intLevel() >= Level.TRACE.intLevel()) return false;
            }
            // A second volatile read rejects an update during inspection. This is the
            // decision's linearization point; overlapping changes follow native snapshots.
            return valid() && root.getFilter() == chain && outputs.get() == snapshot;
        }

        @Override public void close() {
            live = false; // any racing/current filter immediately returns NEUTRAL
            context.removePropertyChangeListener(change);
            for (int attempt = 0; attempt < 16; attempt++) {
                Filter before = root.getFilter();
                var remaining = new ArrayList<Filter>();
                boolean found = false;
                for (Filter entry : entries(before)) {
                    if (entry == gate) found = true;
                    else remaining.add(entry);
                }
                if (!found || filterField.compareAndSet(root, before, compose(remaining))) break;
            }
            gate.stop();
        }
    }

    private static final class Gate extends AbstractFilter {
        final Lease owner;
        Gate(Lease owner) { this.owner = owner; }
        @Override public Result filter(LogEvent event) {
            if (event == null || (event.getClass() != MutableLogEvent.class && event.getClass() != Log4jLogEvent.class))
                return Result.NEUTRAL;
            if (event.getLevel() != Level.TRACE || !NATIVE_LOGGER.equals(event.getLoggerName()) || event.getMarker() != Logging.LOADING)
                return Result.NEUTRAL;
            var message = event.getMessage();
            if (message == null || (message.getClass() != ParameterizedMessage.class
                && message.getClass() != ReusableParameterizedMessage.class && message.getClass() != MutableLogEvent.class))
                return Result.NEUTRAL;
            String format = message.getFormat();
            if (!BEFORE.equals(format) && !AFTER.equals(format)) return Result.NEUTRAL;
            Object[] parameters = message.getParameters();
            if (parameters == null || parameters.length != 3 || !(parameters[0] instanceof EventPriority)
                || !(parameters[1] instanceof String) || parameters[2] == null
                || parameters[2].getClass() != BuildCreativeModeTabContentsEvent.class)
                return Result.NEUTRAL;
            try { return owner.rejectsTrace() ? Result.DENY : Result.NEUTRAL; }
            catch (IllegalAccessException | RuntimeException | LinkageError unsupported) { return Result.NEUTRAL; }
        }
    }
}
