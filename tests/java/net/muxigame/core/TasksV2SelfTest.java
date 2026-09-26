package net.muxigame.core;

import com.google.gson.*;
import net.muxigame.core.feature.tasks.*;
import java.time.*;
import java.util.*;

/** Selection, replacement and event accounting are tested independently of the game's event plumbing. */
public final class TasksV2SelfTest {
    private static int passed;
    private static void check(String name,boolean value) {
        if(!value) throw new AssertionError("Daily tasks v2: "+name); passed++;
    }
    private static Set<String> ids(List<TaskCatalog.Definition> list) {
        Set<String> out=new HashSet<>(); list.forEach(d->out.add(d.id())); return out;
    }
    private static java.util.stream.Stream<JsonObject> rewards(TaskCatalog.Definition d) {
        return d.rewards().stream().flatMap(r->r.has("variants")
            ? java.util.stream.StreamSupport.stream(r.getAsJsonArray("variants").spliterator(),false).map(JsonElement::getAsJsonObject)
            : java.util.stream.Stream.of(r));
    }
    public static int run(TaskCatalog catalog) {
        UUID user=UUID.fromString("a5bfd2f1-72ee-4777-8bbe-0ef789293730");
        Instant now=Instant.parse("2026-09-27T02:00:00Z"); long seed=1731;
        check("all default experience rewards are one level",catalog.pool().stream().allMatch(d->d.experienceLevels()==1));
        check("no bread reward",catalog.pool().stream().flatMap(TasksV2SelfTest::rewards).noneMatch(r->r.get("id").getAsString().equals("minecraft:bread")));
        check("three to five thousand game steps",catalog.pool().stream().filter(d->d.id().equals("trail_walker")).allMatch(d->d.goal()==3000 && d.goalMax()==5000 && d.goalStep()==500 && d.divisor()==100 && d.unit().equals("步")));
        check("ten legendary target sets",catalog.pool().stream().filter(TaskCatalog.Definition::hard).count()==10);
        check("one high-tier kill from two or three alternatives",catalog.pool().stream().filter(TaskCatalog.Definition::hard).allMatch(d->d.goal()==1 && d.minTier()==4 && d.targets().size()>=2 && d.targets().size()<=3));
        check("three shooting variants",catalog.pool().stream().filter(d->d.id().startsWith("shooting_")).count()==3);
        check("arrows and torches half or full stacks",catalog.pool().stream().flatMap(TasksV2SelfTest::rewards).filter(r->Set.of("minecraft:arrow","minecraft:torch").contains(r.get("id").getAsString())).allMatch(r->r.get("count").getAsInt()==32 && r.get("countMax").getAsInt()==64));
        Map<String,Integer> caps=Map.of("coal_miner",32,"iron_miner",16,"copper_miner",24,"zombie_patrol",24,"skeleton_patrol",18,"spider_patrol",18,"gone_fishing",15);
        check("ordinary target caps retained while requirements vary",catalog.pool().stream().filter(d->caps.containsKey(d.id())).allMatch(d->caps.get(d.id())==d.goalMax() && d.goal()<d.goalMax()));

        var state=DailyTaskState.create(catalog,user,seed,now,d->1000);
        check("four unique tasks",state.entries().size()==4 && state.offeredIds().size()==4);
        check("fourth is the sole hard slot",state.entries().get(3).definition.hard() && state.entries().subList(0,3).stream().noneMatch(e->e.definition.hard()));
        check("three normal groups",state.entries().subList(0,3).stream().map(e->e.definition.group()).distinct().count()==3);
        check("one shared free replacement",state.rerollsRemaining()==1);
        String day=state.day().toString(), old=state.entries().get(0).definition.id();
        check("stale reroll rejected",state.reroll(catalog,user,seed,"2000-01-01",old,d->9999)==DailyTaskState.RerollResult.STALE);
        check("unknown reroll rejected",state.reroll(catalog,user,seed,day,"not_assigned",d->9999)==DailyTaskState.RerollResult.UNAVAILABLE);
        check("invalid requests do not use allowance",state.rerollsRemaining()==1);
        check("free replacement succeeds",state.reroll(catalog,user,seed,day,old,d->9999)==DailyTaskState.RerollResult.SUCCESS);
        check("replacement has new baseline",state.entries().get(0).baseline==9999 && state.entries().get(0).progress()==0);
        check("old task cannot be claimed",!state.claimable(day,old));
        check("offered history includes discarded task",state.offeredIds().contains(old) && state.offeredIds().size()==5);
        check("second replacement refused",state.reroll(catalog,user,seed,day,state.entries().get(3).definition.id(),d->0)==DailyTaskState.RerollResult.USED);
        var restored=DailyTaskState.parse(state.json());
        check("relogin preserves replacement and allowance",restored.rerollsRemaining()==0 && restored.json().equals(state.json()));
        var next=DailyTaskState.create(catalog,user,seed,now.plusSeconds(86400),d->20000,restored);
        check("next day avoids original AND replaced tasks",Collections.disjoint(next.offeredIds(),restored.offeredIds()));
        check("allowance renews next day",next.rerollsRemaining()==1);
        check("clock rollback does not refresh",!next.expired(catalog,now));

        var hardState=DailyTaskState.create(catalog,user,seed,now,d->0);
        String hard=hardState.entries().get(3).definition.id();
        check("hard reroll allowed",hardState.reroll(catalog,user,seed,day,hard,d->0)==DailyTaskState.RerollResult.SUCCESS);
        check("hard remains fourth, no extra slot",hardState.entries().size()==4 && hardState.entries().get(3).definition.hard() && !hardState.entries().get(3).definition.id().equals(hard));
        var h=hardState.entries().get(3).definition;
        check("ordinary mob not legendary",!hardState.record(TaskCatalog.Kind.CHAMPION_KILLED,h.targets().get(0),0));
        check("tier three not legendary",!hardState.record(TaskCatalog.Kind.CHAMPION_KILLED,h.targets().get(0),3));
        check("wrong species not legendary task",!hardState.record(TaskCatalog.Kind.CHAMPION_KILLED,"minecraft:cow",5));
        check("tier four correct species completes",hardState.record(TaskCatalog.Kind.CHAMPION_KILLED,h.targets().get(0),4));
        check("cannot replace completed difficulty task",hardState.reroll(catalog,user,seed,day,h.id(),d->0)==DailyTaskState.RerollResult.UNAVAILABLE);
        check("hard task reward once",hardState.markClaimed(day,h.id()) && !hardState.markClaimed(day,h.id()));
        check("higher tier also eligible",h.matches(TaskCatalog.Kind.CHAMPION_KILLED,h.targets().get(0),5));

        var guns=catalog.available(d->d.id().startsWith("shooting_"));
        var g=DailyTaskState.create(guns,user,seed,now,d->0);
        check("ordinary statistics cannot complete event tasks",!g.observe(d->1000000));
        check("real event increments hit count",g.record(TaskCatalog.Kind.GUN_HIT,"minecraft:zombie",0));
        check("hit not a headshot or kill",g.find("shooting_hits").progress()==1 && g.find("shooting_headshots").progress()==0 && g.find("shooting_hunt").progress()==0);
        for(int i=0;i<80;i++) g.record(TaskCatalog.Kind.GUN_HIT,"minecraft:zombie",0);
        int hitGoal=g.find("shooting_hits").definition.goal();
        check("event count capped at goal",g.find("shooting_hits").progress()==hitGoal);
        check("event progress survives save",DailyTaskState.parse(g.json()).find("shooting_hits").progress()==hitGoal);

        var tiny=new TaskCatalog(true,catalog.zone(),0,1,0,List.of(catalog.pool().get(0)));
        var only=DailyTaskState.create(tiny,user,seed,now,d->0);
        check("no candidate does not consume reroll",only.reroll(tiny,user,seed,day,only.entries().get(0).definition.id(),d->0)==DailyTaskState.RerollResult.NO_CANDIDATE && only.rerollsRemaining()==1);
        check("tiny custom pool still fills day",tiny.select(user,only.day().plusDays(1),seed,only.offeredIds()).size()==1);
        JsonObject legacy=JsonParser.parseString(only.json()).getAsJsonObject(); legacy.addProperty("schema",1);
        legacy.remove("offeredIds"); legacy.remove("previousIds"); legacy.remove("rerollsUsed");
        var def=legacy.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("definition");
        def.remove("experienceLevels"); def.addProperty("experience",15);
        check("legacy 15 XP becomes ONE level",DailyTaskState.parse(legacy.toString()).entries().get(0).definition.experienceLevels()==1);
        check("legacy reload has one free change",DailyTaskState.parse(legacy.toString()).rerollsRemaining()==1);

        int pairs=0, repeats=0, shapeErrors=0;
        for(int u=0;u<256;u++) {
            UUID who=new UUID(4291+u,513L*u+19); Set<String> previous=Set.of();
            for(int d=0;d<30;d++) {
                var tasks=catalog.select(who,LocalDate.of(2026,9,1).plusDays(d),seed,previous);
                var chosen=ids(tasks);
                if(d>0) { pairs++; if(!Collections.disjoint(previous,chosen)) repeats++; }
                if(tasks.size()!=4 || !tasks.get(3).hard() || tasks.stream().filter(TaskCatalog.Definition::hard).count()!=1) shapeErrors++;
                previous=chosen;
            }
        }
        check("7424 adjacent days without repeated task",pairs==7424 && repeats==0);
        check("all 7680 assignments keep three plus one",shapeErrors==0);
        System.out.println("Daily selection simulation: 256 players x 30 days, "+pairs+" adjacent pairs, repeats="+repeats);
        return passed;
    }
}
