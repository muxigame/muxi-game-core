package net.muxigame.core.feature.tasks;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Public gameplay configuration. Deliberately separate from the private identity/login configuration. */
public record TaskCatalog(boolean enabled, ZoneId zone, int resetHour, int dailyCount, int hardCount, List<Definition> pool) {
    public static final String FILE = "config/muxi-daily-tasks.json";
    public static final String DEFAULTS = "/muxi/daily-tasks-defaults.json";
    public static final int MAX_TASKS = 5;
    public static final int MAX_REWARDS = 4;
    public enum Kind {
        MINED, CRAFTED, KILLED, CUSTOM, GUN_HIT, GUN_HEADSHOT, GUN_KILL, CHAMPION_KILLED, DEFEATED, HARVESTED, FLOWERS;
        public boolean eventDriven() { return ordinal() >= GUN_HIT.ordinal(); }
    }
    public TaskCatalog(boolean enabled, ZoneId zone, int hour, int count, List<Definition> pool) {
        this(enabled, zone, hour, count, 0, pool);
    }

    public record Definition(String id, String group, String title, String description, Kind kind,
                             List<String> targets, int goal, int divisor, String unit,
                             List<JsonObject> rewards, int experienceLevels, boolean hard, int minTier, List<String> requiresMods,
                             int goalMax, int goalStep) {
        public JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id); o.addProperty("group", group); o.addProperty("title", title);
            o.addProperty("description", description); o.addProperty("kind", kind.name().toLowerCase(Locale.ROOT));
            JsonArray ts = new JsonArray(); targets.forEach(ts::add); o.add("targets", ts);
            o.addProperty("goal", goal); if(goalMax!=goal) o.addProperty("goalMax",goalMax);
            if(goalStep!=1) o.addProperty("goalStep",goalStep);
            o.addProperty("divisor", divisor); o.addProperty("unit", unit);
            JsonArray rs = new JsonArray(); rewards.forEach(r -> rs.add(r.deepCopy())); o.add("rewards", rs);
            o.addProperty("experienceLevels", experienceLevels); o.addProperty("hard", hard); o.addProperty("minTier", minTier);
            JsonArray mods=new JsonArray(); requiresMods.forEach(mods::add); o.add("requiresMods", mods); return o;
        }
        public boolean matches(Kind event, String target, int tier) {
            return kind==event && (targets.isEmpty() || targets.contains(target))
                && (kind!=Kind.CHAMPION_KILLED || tier>=minTier);
        }
        /** Roll only at assignment/replacement, then save a fully concrete definition in player NBT. */
        public Definition materialize(UUID player, LocalDate day, long worldSeed, long salt) {
            Random random=new Random(mix(worldSeed ^ mix(player.getMostSignificantBits()) ^ mix(player.getLeastSignificantBits())
                ^ mix(day.toEpochDay()) ^ mix(id.hashCode()) ^ salt));
            JsonObject value=json();
            int n=goal+goalStep*random.nextInt((goalMax-goal)/goalStep+1);
            value.addProperty("goal",n); value.remove("goalMax");
            value.remove("goalStep");
            value.addProperty("description",description.replace("{goal}",Integer.toString(n)));
            JsonArray rewardArray=value.getAsJsonArray("rewards");
            for(int i=0;i<rewardArray.size();i++) {
                JsonObject reward=rewardArray.get(i).getAsJsonObject();
                if(reward.has("variants")) {
                    JsonArray variants=reward.getAsJsonArray("variants");
                    int total=0;
                    for(JsonElement variant:variants) total+=number(variant.getAsJsonObject(),"weight",1,1,1000);
                    int roll=random.nextInt(total);
                    JsonObject chosen=null;
                    for(JsonElement variant:variants) {
                        JsonObject candidate=variant.getAsJsonObject();
                        roll-=number(candidate,"weight",1,1,1000);
                        if(roll<0) { chosen=candidate.deepCopy(); break; }
                    }
                    if(chosen==null) throw new IllegalStateException("Reward variant selection failed");
                    chosen.remove("weight"); reward=chosen; rewardArray.set(i,reward);
                }
                int min=number(reward,"count",1,1,64), max=number(reward,"countMax",min,min,64);
                int step=number(reward,"countStep",1,1,64);
                reward.addProperty("count",min+step*random.nextInt((max-min)/step+1));
                reward.remove("countMax"); reward.remove("countStep");
            }
            return definition(value);
        }
    }

    public static TaskCatalog load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try (var in = TaskCatalog.class.getResourceAsStream(DEFAULTS)) {
                if (in == null) throw new IOException("Missing packaged daily-task defaults");
                Files.copy(in, path);
            }
        }
        if (Files.size(path) > 131072) throw new IllegalArgumentException("Task config exceeds 128 KiB");
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static TaskCatalog parse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        int schema=number(o, "schema", 1, 1, 2);
        boolean enabled = !o.has("enabled") || booleanValue(o, "enabled");
        ZoneId zone = ZoneId.of(text(o, "timezone", "Asia/Shanghai", 64));
        int hour = number(o, "resetHour", 0, 0, 23);
        int count = number(o, "dailyCount", 3, 1, MAX_TASKS);
        int hard=number(o,"hardCount",schema>=2?1:0,0,1);
        if(count+hard>MAX_TASKS) throw new IllegalArgumentException("Too many task slots");
        JsonArray array = o.getAsJsonArray("pool");
        if (array == null || array.isEmpty() || array.size() > 128) throw new IllegalArgumentException("Invalid task pool size");
        Set<String> ids = new HashSet<>(); List<Definition> pool = new ArrayList<>();
        for (JsonElement e : array) {
            Definition d = definition(e.getAsJsonObject());
            if (!ids.add(d.id())) throw new IllegalArgumentException("Duplicate task id: " + d.id());
            pool.add(d);
        }
        if (count > pool.stream().filter(d->!d.hard()).count() || hard>pool.stream().filter(Definition::hard).count())
            throw new IllegalArgumentException("Task pool does not fill normal/hard slots");
        return new TaskCatalog(enabled, zone, hour, count, hard, List.copyOf(pool));
    }

    public static Definition definition(JsonObject o) {
        String id = text(o, "id", null, 48);
        String group = text(o, "group", "general", 48);
        if (!id.matches("[a-z0-9_-]+") || !group.matches("[a-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid task id/group");
        String title = text(o, "title", null, 64);
        String description = text(o, "description", title, 256);
        Kind kind = Kind.valueOf(text(o, "kind", null, 24).toUpperCase(Locale.ROOT));
        JsonArray a = o.getAsJsonArray("targets");
        boolean gun=kind==Kind.GUN_HIT || kind==Kind.GUN_HEADSHOT || kind==Kind.GUN_KILL || kind==Kind.HARVESTED || kind==Kind.FLOWERS;
        if (a == null || (!gun && a.isEmpty()) || a.size() > 16) throw new IllegalArgumentException("Invalid task targets");
        List<String> targets = new ArrayList<>();
        for (JsonElement e : a) {
            String target = e.getAsString();
            if (!target.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || target.length() > 128 || targets.contains(target))
                throw new IllegalArgumentException("Invalid or duplicate stat target");
            targets.add(target);
        }
        int goal = number(o, "goal", 1, 1, 1000000);
        int goalMax = number(o,"goalMax",goal,goal,1000000);
        int goalStep = number(o,"goalStep",1,1,1000000);
        if((goalMax-goal)%goalStep!=0) throw new IllegalArgumentException("Goal range must align with goalStep");
        int divisor = number(o, "divisor", 1, 1, 1000000);
        if(kind.eventDriven() && divisor!=1) throw new IllegalArgumentException("Event tasks cannot use a divisor");
        boolean hard=o.has("hard") && booleanValue(o,"hard");
        int minTier=number(o,"minTier",kind==Kind.CHAMPION_KILLED?4:0,0,100);
        if(kind==Kind.CHAMPION_KILLED && minTier<1) throw new IllegalArgumentException("Champion tier required");
        List<String> mods=new ArrayList<>();
        if(o.has("requiresMods")) {
            JsonArray names=o.getAsJsonArray("requiresMods");
            if(names.size()>16) throw new IllegalArgumentException("Too many mod requirements");
            for(JsonElement name:names) {
                String s=name.getAsString();
                if(!s.matches("[a-z0-9_]{1,64}") || mods.contains(s)) throw new IllegalArgumentException("Invalid mod requirement");
                mods.add(s);
            }
        }
        String unit = text(o, "unit", "", 8);
        JsonArray rs = o.getAsJsonArray("rewards");
        if (rs == null || rs.size() > MAX_REWARDS) throw new IllegalArgumentException("At most four reward stacks per task");
        List<JsonObject> rewards = new ArrayList<>();
        for (JsonElement e : rs) {
            JsonObject reward = e.getAsJsonObject().deepCopy();
            if (reward.toString().length() > 4096) throw new IllegalArgumentException("Reward components too large");
            if(reward.has("variants")) {
                if(reward.size()!=1 || !reward.get("variants").isJsonArray()) throw new IllegalArgumentException("Reward variants must be the only field");
                JsonArray variants=reward.getAsJsonArray("variants");
                if(variants.isEmpty() || variants.size()>16) throw new IllegalArgumentException("Invalid reward variant count");
                for(JsonElement variant:variants) validateReward(variant.getAsJsonObject(),true);
            } else validateReward(reward,false);
            rewards.add(reward);
        }
        // Legacy +10/+15 points migrate to ONE level, never ten/fifteen levels.
        int levels = number(o, "experienceLevels", number(o,"experience",0,0,10000)>0?1:0, 0, 100);
        if (rewards.isEmpty() && levels == 0) throw new IllegalArgumentException("Task has no reward");
        return new Definition(id, group, title, description, kind, List.copyOf(targets), goal, divisor, unit,
            List.copyOf(rewards), levels, hard, minTier, List.copyOf(mods),goalMax,goalStep);
    }

    private static void validateReward(JsonObject reward,boolean variant) {
        if(reward.toString().length()>4096) throw new IllegalArgumentException("Reward components too large");
        String item=text(reward,"id",null,128);
        if(!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid reward item");
        int count=number(reward,"count",1,1,64), max=number(reward,"countMax",count,count,64);
        int step=number(reward,"countStep",1,1,64);
        if((max-count)%step!=0) throw new IllegalArgumentException("Reward range must align with countStep");
        if(variant) number(reward,"weight",1,1,1000);
    }

    public LocalDate day(Instant now) {
        LocalDate today=now.atZone(zone).toLocalDate();
        // Compare the actual local reset boundary, not a duration subtraction (DST days are not always 24 hours).
        return now.isBefore(today.atTime(resetHour,0).atZone(zone).toInstant())?today.minusDays(1):today;
    }
    public Instant nextReset(LocalDate day) { return day.plusDays(1).atTime(resetHour, 0).atZone(zone).toInstant(); }

    /** Stable per player/day, with a separate hard slot and previous task-day avoidance. */
    public List<Definition> select(UUID player, LocalDate date, long worldSeed) {
        return select(player,date,worldSeed,Set.of());
    }
    public List<Definition> select(UUID player, LocalDate date, long worldSeed, Set<String> previous) {
        List<Definition> shuffled=shuffle(player,date,worldSeed,0), selected=new ArrayList<>();
        selected.addAll(pick(shuffled.stream().filter(d->!d.hard()).toList(),dailyCount,previous));
        selected.addAll(pick(shuffled.stream().filter(Definition::hard).toList(),hardCount,previous));
        return List.copyOf(selected);
    }
    private static List<Definition> pick(List<Definition> pool,int count,Set<String> previous) {
        List<Definition> out=new ArrayList<>(); Set<String> groups=new HashSet<>();
        for(boolean repeated:new boolean[]{false,true}) {
            for(Definition d:pool) if(previous.contains(d.id())==repeated && out.size()<count && groups.add(d.group())) out.add(d);
            for(Definition d:pool) if(previous.contains(d.id())==repeated && out.size()<count && !out.contains(d)) out.add(d);
        }
        return out;
    }
    public Optional<Definition> replacement(UUID player,LocalDate day,long seed,Definition old,
                                            Set<String> offered,Set<String> previous) {
        List<Definition> options=shuffle(player,day,seed,0x5245524f4c4cL).stream()
            .filter(d->d.hard()==old.hard() && !offered.contains(d.id())).toList();
        for(boolean repeated:new boolean[]{false,true}) {
            var same=options.stream().filter(d->previous.contains(d.id())==repeated && d.group().equals(old.group())).findFirst();
            if(same.isPresent()) return same;
            var other=options.stream().filter(d->previous.contains(d.id())==repeated).findFirst();
            if(other.isPresent()) return other;
        }
        return Optional.empty();
    }
    public TaskCatalog available(java.util.function.Predicate<Definition> available) {
        List<Definition> tasks=pool.stream().filter(available).toList();
        int normal=(int)tasks.stream().filter(d->!d.hard()).count(), hard=(int)tasks.stream().filter(Definition::hard).count();
        return new TaskCatalog(enabled && normal>0,zone,resetHour,Math.min(dailyCount,normal),Math.min(hardCount,hard),tasks);
    }
    private List<Definition> shuffle(UUID player,LocalDate date,long worldSeed,long salt) {
        List<Definition> shuffled = new ArrayList<>(pool);
        long seed=mix(worldSeed ^ mix(player.getMostSignificantBits()) ^ mix(player.getLeastSignificantBits()) ^ mix(date.toEpochDay()) ^ salt);
        Collections.shuffle(shuffled,new Random(seed)); return shuffled;
    }
    private static long mix(long x) { x=(x^(x>>>30))*0xbf58476d1ce4e5b9L; x=(x^(x>>>27))*0x94d049bb133111ebL; return x^(x>>>31); }

    private static boolean booleanValue(JsonObject o, String key) {
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Expected boolean: " + key);
        return o.get(key).getAsBoolean();
    }
    public static int number(JsonObject o, String key, int fallback, int min, int max) {
        if (!o.has(key)) return fallback;
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !e.getAsString().matches("[0-9]+"))
            throw new IllegalArgumentException("Expected integer: " + key);
        long n = e.getAsLong();
        if (n < min || n > max) throw new IllegalArgumentException("Out of range: " + key);
        return (int)n;
    }
    private static String text(JsonObject o, String key, String fallback, int max) {
        if (!o.has(key)) { if (fallback != null) return fallback; throw new IllegalArgumentException("Missing: " + key); }
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected text: " + key);
        String s = e.getAsString();
        if (s.length() > max || s.chars().anyMatch(c -> Character.isISOControl(c) || c == 0x00a7 || (c >= 0x202a && c <= 0x202e)))
            throw new IllegalArgumentException("Invalid text: " + key);
        return s;
    }
}
