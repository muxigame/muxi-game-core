package net.muxigame.core.feature.tasks;

import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.util.function.ToLongFunction;

/** Pure task rules. Persisted with the player's inventory, never keyed by mutable nickname. */
public final class DailyTaskState {
    public static final class Entry {
        public final TaskCatalog.Definition definition;
        public final long baseline;
        private int progress;
        private boolean claimed;
        private Entry(TaskCatalog.Definition d, long b, int p, boolean c) { definition=d; baseline=b; progress=p; claimed=c; }
        public int progress() { return progress; }
        public boolean claimed() { return claimed; }
        public boolean ready() { return progress >= definition.goal() && !claimed; }
        public boolean replaceable() { return !claimed && progress < definition.goal(); }
    }
    private final LocalDate day;
    private final long resetAt;
    private final List<Entry> entries;
    private final Set<String> previousIds=new LinkedHashSet<>(), offeredIds=new LinkedHashSet<>();
    private int rerollsUsed;
    public enum RerollResult { SUCCESS, STALE, UNAVAILABLE, USED, NO_CANDIDATE }
    private DailyTaskState(LocalDate day, long resetAt, List<Entry> entries) {
        this.day=day; this.resetAt=resetAt; this.entries=new ArrayList<>(entries);
        entries.forEach(e->offeredIds.add(e.definition.id()));
    }
    public LocalDate day() { return day; }
    public long resetAt() { return resetAt; }
    public List<Entry> entries() { return List.copyOf(entries); }
    public Set<String> offeredIds() { return Collections.unmodifiableSet(offeredIds); }
    public Set<String> previousIds() { return Collections.unmodifiableSet(previousIds); }
    public int rerollsRemaining() { return Math.max(0,1-rerollsUsed); }
    public boolean expired(TaskCatalog catalog, Instant now) { return catalog.day(now).isAfter(day); }

    public static DailyTaskState create(TaskCatalog catalog, UUID player, long seed, Instant now,
                                        ToLongFunction<TaskCatalog.Definition> counter) {
        return create(catalog,player,seed,now,counter,null);
    }
    public static DailyTaskState create(TaskCatalog catalog, UUID player, long seed, Instant now,
                                        ToLongFunction<TaskCatalog.Definition> counter, DailyTaskState previous) {
        LocalDate day = catalog.day(now);
        Set<String> last=previous==null?Set.of():previous.offeredIds;
        DailyTaskState state=new DailyTaskState(day, catalog.nextReset(day).getEpochSecond(), catalog.select(player, day, seed,last).stream()
            .map(d -> d.materialize(player,day,seed,0))
            .map(d -> new Entry(d, Math.max(0, counter.applyAsLong(d)), 0, false)).toList());
        state.previousIds.addAll(last); return state;
    }
    public boolean observe(ToLongFunction<TaskCatalog.Definition> counter) {
        boolean changed = false;
        for (Entry e : entries) {
            if (!e.replaceable() || e.definition.kind().eventDriven()) continue;
            long delta = Math.max(0, counter.applyAsLong(e.definition) - e.baseline);
            int value = (int)Math.min(e.definition.goal(), delta / e.definition.divisor());
            if (value > e.progress) { e.progress=value; changed=true; }
        }
        return changed;
    }
    public boolean record(TaskCatalog.Kind kind,String target,int tier) {
        return record(kind,target,tier,null);
    }
    /** Delayed death commits may only credit tasks that existed when that death happened. */
    public boolean record(TaskCatalog.Kind kind,String target,int tier,Set<String> eligibleIds) {
        return record(kind,target,tier,eligibleIds,1);
    }
    public boolean record(TaskCatalog.Kind kind,String target,int tier,Set<String> eligibleIds,int amount) {
        if(!kind.eventDriven()) throw new IllegalArgumentException("Not an event counter");
        if(amount<=0) return false;
        boolean changed=false;
        for(Entry e:entries) if(e.replaceable() && (eligibleIds==null || eligibleIds.contains(e.definition.id()))
            && e.definition.matches(kind,target,tier)) { e.progress=(int)Math.min(e.definition.goal(),(long)e.progress+amount); changed=true; }
        return changed;
    }
    public RerollResult reroll(TaskCatalog catalog,UUID player,long seed,String expectedDay,String id,
                               ToLongFunction<TaskCatalog.Definition> counter) {
        if(!day.toString().equals(expectedDay)) return RerollResult.STALE;
        Entry old=find(id); if(old==null || !old.replaceable()) return RerollResult.UNAVAILABLE;
        if(rerollsRemaining()==0) return RerollResult.USED;
        var next=catalog.replacement(player,day,seed,old.definition,offeredIds,previousIds);
        if(next.isEmpty()) return RerollResult.NO_CANDIDATE;
        var d=next.get().materialize(player,day,seed,0x5245524f4c4cL); long baseline=Math.max(0,counter.applyAsLong(d));
        entries.set(entries.indexOf(old),new Entry(d,baseline,0,false));
        offeredIds.add(d.id()); rerollsUsed++; return RerollResult.SUCCESS;
    }
    public Entry find(String id) { return entries.stream().filter(e -> e.definition.id().equals(id)).findFirst().orElse(null); }
    public boolean claimable(String expectedDay, String id) {
        Entry e = find(id); return day.toString().equals(expectedDay) && e != null && e.ready();
    }
    public boolean markClaimed(String expectedDay, String id) {
        if (!claimable(expectedDay, id)) return false;
        find(id).claimed=true; return true;
    }
    public String json() {
        JsonObject o = new JsonObject(); o.addProperty("schema", 2); o.addProperty("day", day.toString()); o.addProperty("resetAt", resetAt);
        o.addProperty("rerollsUsed",rerollsUsed);
        JsonArray previous=new JsonArray(), offered=new JsonArray(); previousIds.forEach(previous::add); offeredIds.forEach(offered::add);
        o.add("previousIds",previous); o.add("offeredIds",offered);
        JsonArray a = new JsonArray();
        for (Entry e : entries) {
            JsonObject v = new JsonObject(); v.add("definition", e.definition.json()); v.addProperty("baseline", e.baseline);
            v.addProperty("progress", e.progress); v.addProperty("claimed", e.claimed); a.add(v);
        }
        o.add("entries", a); return o.toString();
    }
    public static DailyTaskState parse(String json) {
        if (json.length() > 131072) throw new IllegalArgumentException("Task state too large");
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        int schema=TaskCatalog.number(o, "schema", 1, 1, 2);
        LocalDate day = LocalDate.parse(o.get("day").getAsString());
        long resetAt = o.get("resetAt").getAsLong();
        JsonArray a = o.getAsJsonArray("entries");
        if (a == null || a.isEmpty() || a.size() > TaskCatalog.MAX_TASKS) throw new IllegalArgumentException("Invalid saved task count");
        List<Entry> entries = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonElement v : a) {
            JsonObject e = v.getAsJsonObject(); TaskCatalog.Definition d = TaskCatalog.definition(e.getAsJsonObject("definition"));
            long baseline = e.get("baseline").getAsLong();
            if (baseline < 0 || baseline > 16L * Integer.MAX_VALUE || !ids.add(d.id())) throw new IllegalArgumentException("Invalid saved task baseline/id");
            int p = TaskCatalog.number(e, "progress", 0, 0, d.goal());
            boolean c = e.get("claimed").getAsBoolean();
            if (c && p < d.goal()) throw new IllegalArgumentException("Incomplete claimed task");
            entries.add(new Entry(d, baseline, p, c));
        }
        DailyTaskState state=new DailyTaskState(day, resetAt, entries);
        if(schema>=2) {
            state.rerollsUsed=TaskCatalog.number(o,"rerollsUsed",0,0,1);
            state.previousIds.addAll(history(o,"previousIds"));
            Set<String> offered=history(o,"offeredIds");
            if(!offered.containsAll(ids) || offered.size()!=entries.size()+state.rerollsUsed)
                throw new IllegalArgumentException("Inconsistent task reroll history");
            state.offeredIds.clear(); state.offeredIds.addAll(offered);
        }
        return state;
    }
    private static Set<String> history(JsonObject o,String key) {
        JsonArray a=o.getAsJsonArray(key);
        if(a==null || a.size()>TaskCatalog.MAX_TASKS+1) throw new IllegalArgumentException("Invalid task history");
        Set<String> result=new LinkedHashSet<>();
        for(JsonElement e:a) {
            String id=e.getAsString();
            if(!id.matches("[a-z0-9_-]{1,48}") || !result.add(id)) throw new IllegalArgumentException("Invalid history ID");
        }
        return result;
    }
}
