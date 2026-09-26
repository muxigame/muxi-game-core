package net.muxigame.core;

import net.muxigame.core.feature.tasks.*;
import com.google.gson.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.function.ToLongFunction;

/** No Minecraft bootstrap: date/accounting/claim/packing rules can be exercised deterministically offline. */
public final class TasksSelfTest {
    private static int passed;
    private static void check(String name,boolean condition) {
        if(!condition) throw new AssertionError("Daily tasks: "+name); passed++;
    }
    private static void rejects(String name,Runnable operation) {
        try { operation.run(); } catch(RuntimeException expected) { passed++; return; }
        throw new AssertionError("Expected rejection: "+name);
    }
    public static int run() {
        try {
            String json;
            try(var in=TaskCatalog.class.getResourceAsStream(TaskCatalog.DEFAULTS)) {
                if(in==null) throw new AssertionError("Packaged task defaults missing");
                json=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            }
            TaskCatalog full=TaskCatalog.parse(json);
            check("3 normal plus 1 hard from 27",full.dailyCount()==3 && full.hardCount()==1 && full.pool().size()==27);
            TaskCatalog catalog=full.available(d->!d.hard() && !d.kind().eventDriven());
            check("enabled by default",catalog.enabled());
            check("3 tasks from 9",catalog.dailyCount()==3 && catalog.pool().size()==9);
            check("Shanghai midnight",catalog.zone().equals(ZoneId.of("Asia/Shanghai")) && catalog.resetHour()==0);
            Instant before=Instant.parse("2026-09-26T15:59:59Z"), midnight=before.plusSeconds(1);
            check("before reset",catalog.day(before).equals(LocalDate.of(2026,9,26)));
            check("at reset",catalog.day(midnight).equals(LocalDate.of(2026,9,27)));
            check("next reset timestamp",catalog.nextReset(catalog.day(before)).equals(midnight));
            TaskCatalog dst=new TaskCatalog(true,ZoneId.of("America/Los_Angeles"),4,3,catalog.pool());
            check("DST fall boundary before",dst.day(Instant.parse("2026-11-01T11:30:00Z")).equals(LocalDate.of(2026,10,31)));
            check("DST fall boundary after",dst.day(Instant.parse("2026-11-01T12:00:00Z")).equals(LocalDate.of(2026,11,1)));
            UUID player=UUID.fromString("bac84aa3-61d5-3a42-acb0-02ab44e17ee2");
            var selected=catalog.select(player,catalog.day(before),42);
            check("deterministic",selected.equals(catalog.select(player,catalog.day(before),42)));
            check("no duplicates",selected.stream().map(TaskCatalog.Definition::id).distinct().count()==3);
            check("stats-only subset covers its two categories",selected.stream().map(TaskCatalog.Definition::group).distinct().count()==2);
            var oneGroup=new TaskCatalog(true,catalog.zone(),0,3,catalog.pool().stream().filter(d->d.group().equals("mining")).toList());
            check("fills from same category",oneGroup.select(player,catalog.day(before),42).size()==3);
            Set<String> variants=new HashSet<>();
            for(int i=0;i<8;i++) variants.add(catalog.select(player,catalog.day(before).plusDays(i),42).toString());
            check("rotates on different days",variants.size()>1);
            ToLongFunction<TaskCatalog.Definition> baseline=d->5000;
            DailyTaskState state=DailyTaskState.create(catalog,player,42,before,baseline);
            check("new day starts at zero",state.entries().stream().allMatch(e->e.progress()==0));
            check("historical stats do not count",!state.observe(baseline));
            check("incomplete cannot claim",!state.markClaimed(state.day().toString(),state.entries().get(0).definition.id()));
            check("increment",state.observe(d->5000+3L*d.divisor()));
            check("uses correct divisor",state.entries().stream().allMatch(e->e.progress()==Math.min(3,e.definition.goal())));
            String saved=state.json(); state=DailyTaskState.parse(saved);
            check("state round trip",saved.equals(state.json()));
            check("counter rollback never removes progress",!state.observe(d->0));
            check("caps progress",state.observe(d->Long.MAX_VALUE/2));
            check("all capped at goals",state.entries().stream().allMatch(e->e.progress()==e.definition.goal()));
            String id=state.entries().get(0).definition.id(), day=state.day().toString();
            check("previous date rejected",!state.markClaimed("2026-09-25",id));
            check("unknown task rejected",!state.markClaimed(day,"not_assigned"));
            check("first claim accepted",state.markClaimed(day,id));
            check("duplicate claim rejected",!state.markClaimed(day,id));
            state=DailyTaskState.parse(state.json());
            check("relogin preserves claimed bit",state.find(id).claimed() && !state.claimable(day,id));
            check("no reset within day",!state.expired(catalog,before));
            check("reset at midnight",state.expired(catalog,midnight));
            check("clock rollback cannot reroll",!state.expired(catalog,before.minusSeconds(86400)));
            check("new day has new baseline",DailyTaskState.create(catalog,player,42,midnight,d->100000).entries().stream().allMatch(e->e.baseline==100000 && e.progress()==0 && !e.claimed()));
            String old=state.entries().get(0).definition.json().toString();
            check("reload cannot silently replace saved rewards",DailyTaskState.parse(state.json()).entries().get(0).definition.json().toString().equals(old));
            rejects("bad timezone",()->TaskCatalog.parse(json.replace("Asia/Shanghai","Not/A_Zone")));
            rejects("fractional task count",()->TaskCatalog.parse(json.replace("\"dailyCount\": 3","\"dailyCount\": 3.5")));
            rejects("too many tasks",()->TaskCatalog.parse(json.replace("\"dailyCount\": 3","\"dailyCount\": 20")));
            rejects("unsupported schema",()->TaskCatalog.parse(json.replace("\"schema\": 2","\"schema\": 3")));
            JsonObject zeroGoal=JsonParser.parseString(json).getAsJsonObject();
            zeroGoal.getAsJsonArray("pool").get(0).getAsJsonObject().addProperty("goal",0);
            rejects("zero goal",()->TaskCatalog.parse(zeroGoal.toString()));
            rejects("duplicate task id",()->TaskCatalog.parse(json.replace("\"iron_miner\"","\"coal_miner\"")));
            JsonObject invalid=JsonParser.parseString(json).getAsJsonObject();
            invalid.getAsJsonArray("pool").get(0).getAsJsonObject().addProperty("divisor",0);
            rejects("zero divisor",()->TaskCatalog.parse(invalid.toString()));
            rejects("corrupt state never silently resets",()->DailyTaskState.parse("{}"));
            var inventory=List.of(new RewardPacking.Slot<>("iron",62,64),new RewardPacking.Slot<String>(null,0,64));
            var packed=RewardPacking.plan(inventory,List.of(new RewardPacking.Slot<>("iron",4,64))).orElseThrow();
            check("fills matching stack first",packed.get(0).count()==64);
            check("remainder uses empty slot",packed.get(1).count()==2 && packed.get(1).kind().equals("iron"));
            check("simulation did not mutate original",inventory.get(0).count()==62 && inventory.get(1).count()==0);
            check("different components do not merge",RewardPacking.plan(List.of(new RewardPacking.Slot<>("sword_lore_A",1,1)),List.of(new RewardPacking.Slot<>("sword_lore_B",1,1))).isEmpty());
            check("all rewards or none",RewardPacking.plan(inventory,List.of(new RewardPacking.Slot<>("iron",4,64),new RewardPacking.Slot<>("bread",4,64))).isEmpty());
            check("stackable at full slot count",RewardPacking.plan(List.of(new RewardPacking.Slot<>("iron",60,64)),List.of(new RewardPacking.Slot<>("iron",4,64))).isPresent());
            check("unstackable limits",RewardPacking.plan(List.of(new RewardPacking.Slot<String>(null,0,64)),List.of(new RewardPacking.Slot<>("tool",2,1))).isEmpty());
            check("16 item stack limit",RewardPacking.plan(List.of(new RewardPacking.Slot<>("pearl",15,16)),List.of(new RewardPacking.Slot<>("pearl",2,16))).isEmpty());
            check("xp-only reward inventory unchanged",RewardPacking.plan(inventory,List.of()).orElseThrow().equals(inventory));
            passed+=TasksV2SelfTest.run(full);
            passed+=TasksV3SelfTest.run(full);
            System.out.println("Daily task rule tests: "+passed+" passed"); return passed;
        } catch(java.io.IOException e) { throw new AssertionError(e); }
    }
}
