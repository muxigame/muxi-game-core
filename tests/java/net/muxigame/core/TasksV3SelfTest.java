package net.muxigame.core;

import com.google.gson.*;
import net.muxigame.core.feature.tasks.*;
import java.time.*;
import java.util.*;

/** Template ranges must become immutable concrete assignments, not a second roll at claim/login. */
public final class TasksV3SelfTest {
    private static int passed;
    private static void check(String name,boolean ok) { if(!ok) throw new AssertionError("Tasks v3: "+name); passed++; }
    private static java.util.stream.Stream<JsonObject> rewards(TaskCatalog.Definition d) {
        return d.rewards().stream().flatMap(r->r.has("variants")
            ? java.util.stream.StreamSupport.stream(r.getAsJsonArray("variants").spliterator(),false).map(JsonElement::getAsJsonObject)
            : java.util.stream.Stream.of(r));
    }
    public static int run(TaskCatalog catalog) {
        check("normal rewards never enchanted apples",catalog.pool().stream().filter(d->!d.hard()).flatMap(TasksV3SelfTest::rewards).noneMatch(r->r.get("id").getAsString().equals("minecraft:enchanted_golden_apple")));
        check("all food rewards removed",catalog.pool().stream().flatMap(TasksV3SelfTest::rewards).noneMatch(r->Set.of("minecraft:bread","minecraft:cooked_beef").contains(r.get("id").getAsString())));
        check("powder stays 16/24/32",catalog.pool().stream().flatMap(TasksV3SelfTest::rewards).filter(r->r.get("id").getAsString().equals("minecraft:gunpowder")).allMatch(r->Set.of(16,24,32).contains(r.get("count").getAsInt())));
        check("mining reward denominations",catalog.pool().stream().filter(d->d.group().equals("mining")).flatMap(TasksV3SelfTest::rewards).allMatch(r->Set.of(2,4,8,16,32).contains(r.get("count").getAsInt()) && (!r.has("countMax") || Set.of(2,4,8,16,32).contains(r.get("countMax").getAsInt()))));
        Set<String> miningItems=new HashSet<>(); catalog.pool().stream().filter(d->d.group().equals("mining")).flatMap(TasksV3SelfTest::rewards).forEach(r->miningItems.add(r.get("id").getAsString()));
        check("mining pool includes rich ore spectrum",miningItems.containsAll(Set.of("minecraft:diamond","minecraft:emerald","minecraft:gold_ingot","minecraft:iron_ingot","minecraft:lapis_lazuli","minecraft:redstone","minecraft:copper_ingot","minecraft:coal")));
        Set<String> species=new HashSet<>(); catalog.pool().stream().filter(TaskCatalog.Definition::hard).forEach(d->species.addAll(d.targets()));
        check("all eight legendary species",species.size()==8 && species.containsAll(Set.of("minecraft:phantom","minecraft:drowned","touhou_little_maid:fairy")));
        var foods=catalog.pool().stream().filter(d->d.id().startsWith("food_")).toList();
        check("four actual mod food objectives",foods.size()==4 && foods.stream().allMatch(d->d.goalMax()<=4 && d.requiresMods().contains("farmersdelight") && d.rewards().get(0).get("id").getAsString().equals("minecraft:emerald")));
        check("food emerald rewards span four through eight",foods.stream().allMatch(d->{
            var reward=d.rewards().get(0);
            return reward.get("count").getAsInt()==4 && reward.get("countMax").getAsInt()==8 && !reward.has("countStep");
        }));
        check("every ordinary task has a randomized requirement",catalog.pool().stream().filter(d->!d.hard()).allMatch(d->d.goalMax()>d.goal()));
        check("new kill objectives use events not additive vanilla statistics",catalog.pool().stream().filter(d->d.id().endsWith("_patrol")).allMatch(d->d.kind()==TaskCatalog.Kind.DEFEATED));
        var rangePool=catalog.available(d->Set.of("harvest_crops","gather_flowers","legendary_fairy").contains(d.id()));
        Set<Integer> crops=new HashSet<>(),flowers=new HashSet<>(),apples=new HashSet<>(),emeralds=new HashSet<>();
        Instant now=Instant.parse("2026-09-27T05:00:00Z");
        for(int i=0;i<256;i++) {
            UUID player=new UUID(981,i); var state=DailyTaskState.create(rangePool,player,19,now,d->0);
            check("same assignment reproducible",state.json().equals(DailyTaskState.create(rangePool,player,19,now,d->0).json()));
            check("snapshot stable on reload",state.json().equals(DailyTaskState.parse(state.json()).json()));
            for(var e:state.entries()) {
                var d=e.definition;
                check("concrete goal not rerolled on login",d.goal()==d.goalMax() && !d.description().contains("{goal}"));
                if(d.id().equals("harvest_crops")) crops.add(d.goal());
                if(d.id().equals("gather_flowers")) flowers.add(d.goal());
                for(var r:d.rewards()) {
                    check("concrete reward has no reroll metadata",!r.has("countMax") && !r.has("countStep"));
                    if(r.get("id").getAsString().equals("minecraft:enchanted_golden_apple")) apples.add(r.get("count").getAsInt());
                    else emeralds.add(r.get("count").getAsInt());
                }
            }
        }
        check("crop 4/8/12/16 range",crops.equals(Set.of(4,8,12,16)));
        check("flower full 1..4 range",flowers.equals(Set.of(1,2,3,4)));
        check("apple 1 or 2",apples.equals(Set.of(1,2)));
        check("emerald 4 through 8",emeralds.equals(Set.of(4,5,6,7,8)));
        var c=DailyTaskState.create(rangePool,new UUID(11,22),19,now,d->0);
        var hard=c.entries().stream().filter(e->e.definition.hard()).findFirst().orElseThrow();
        check("second alternative satisfies OR target",c.record(TaskCatalog.Kind.CHAMPION_KILLED,hard.definition.targets().get(1),4));
        check("one OR target is enough",hard.ready());
        var crop=c.find("harvest_crops");
        check("nonpositive amount rejected",!c.record(TaskCatalog.Kind.HARVESTED,"minecraft:wheat",0,null,0));
        check("one mature crop adds one",c.record(TaskCatalog.Kind.HARVESTED,"minecraft:wheat",0,null,1) && crop.progress()==1);
        check("overflow safely capped",c.record(TaskCatalog.Kind.HARVESTED,"minecraft:wheat",0,null,Integer.MAX_VALUE) && crop.progress()==crop.definition.goal());
        check("final serialized state is fixed",DailyTaskState.parse(c.json()).json().equals(c.json()));

        Set<String> seenTypes=new HashSet<>();
        for(int i=0;i<1024;i++) seenTypes.addAll(catalog.select(new UUID(7001,i),LocalDate.of(2026,9,27),91).stream().filter(d->!d.hard()).map(TaskCatalog.Definition::id).toList());
        check("random type selection reaches every ordinary template",seenTypes.containsAll(catalog.pool().stream().filter(d->!d.hard()).map(TaskCatalog.Definition::id).toList()));
        for(var template:catalog.pool().stream().filter(d->!d.hard()).toList()) {
            Set<Integer> goals=new HashSet<>();
            for(int i=0;i<256;i++) {
                var concrete=template.materialize(new UUID(8123,i),LocalDate.of(2026,9,27),177,0);
                goals.add(concrete.goal());
                check("materialized goal follows template step",concrete.goal()>=template.goal() && concrete.goal()<=template.goalMax()
                    && (concrete.goal()-template.goal())%template.goalStep()==0 && concrete.goal()==concrete.goalMax() && concrete.goalStep()==1);
            }
            check("ordinary requirement actually varies: "+template.id(),goals.size()>1);
        }
        return passed;
    }
}
