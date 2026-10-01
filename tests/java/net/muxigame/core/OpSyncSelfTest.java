package net.muxigame.core;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.muxigame.core.feature.identity.*;
import net.muxigame.core.config.CoreConfig;

public final class OpSyncSelfTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name); passed++;
    }
    private static OpSyncJournal.Desired desired(long revision,int level) {
        return new OpSyncJournal.Desired("10091",IdentityRules.offlineUuid("10091"),revision,level);
    }
    private static final class NativeFixture implements OpSyncJournal.Target {
        int level,writes; boolean fail;
        public void setLevel(OpSyncJournal.Desired d) throws IOException {
            if (fail) throw new IOException("synthetic disk failure");
            level=d.level();writes++;
        }
        public int level(OpSyncJournal.Desired d) { return level; }
    }
    private static void rejects(Runnable run) {
        try {run.run();throw new AssertionError("expected rejection");}
        catch (IllegalArgumentException error) {passed++;}
    }
    public static int run() {
        try {
            String key="synthetic-configuration-secret-00000000";
            String config="{\"schema\":1,\"features\":{\"identity\":{\"enabled\":true,\"endpoint\":\"https://example.test/identity/\",\"serverKey\":\""+key+"\"},"
                +"\"login\":{\"enabled\":true,\"endpoint\":\"https://example.test/join/\",\"serverKey\":\""+key+"\"},"
                +"\"opSync\":{\"enabled\":true,\"endpoint\":\"https://example.test/api/internal/game/ops-sync/\",\"serverKey\":\""+key+"\"}}}";
            check("OP configuration enabled with authenticated identity",CoreConfig.parse(config).opSync().enabled());
            check("OP configuration redacts credentials",!CoreConfig.parse(config).toString().contains(key));
            rejects(()->CoreConfig.parse(config.replace("\"login\":{\"enabled\":true","\"login\":{\"enabled\":false")));
            rejects(()->CoreConfig.parse(config.replace("\"identity\":{\"enabled\":true","\"identity\":{\"enabled\":false")));
            rejects(()->CoreConfig.parse(config.replace("/api/internal/game/ops-sync/","/wrong/")));
            Path dir=Files.createTempDirectory("op-sync-self-test-");Path file=dir.resolve("journal.json");
            NativeFixture target=new NativeFixture();OpSyncJournal journal=new OpSyncJournal(file);
            check("offline grant applied",journal.consume(desired(1,4),target)==4 && target.writes==1);
            check("duplicate event is idempotent",journal.consume(desired(1,4),target)==4 && target.writes==1);
            target.level=2;
            check("local change survives same event",journal.consume(desired(1,4),target)==2 && target.writes==1);
            journal=new OpSyncJournal(file);
            check("local change survives restart",journal.consume(desired(1,4),target)==2 && target.writes==1);
            check("new explicit same-level event overrides",journal.consume(desired(2,4),target)==4 && target.writes==2);
            check("offline revoke applied",journal.consume(desired(3,0),target)==0 && target.writes==3);
            try {journal.consume(desired(2,4),target);throw new AssertionError("stale event");}
            catch(IOException expected) {check("stale event cannot mutate",target.writes==3);}
            try {journal.consume(desired(3,4),target);throw new AssertionError("conflicting event");}
            catch(IOException expected) {check("same version conflict cannot mutate",target.writes==3);}

            target.fail=true;
            try {journal.consume(desired(4,3),target);throw new AssertionError("native failure");}
            catch(IOException expected) {check("native failure leaves durable recovery record",journal.hasPending());}
            target.fail=false;journal=new OpSyncJournal(file);journal.recover(target);
            check("restart retries unfinished native save",target.level==3 && !journal.hasPending());
            int writes=target.writes;
            check("retry after recovery does not reapply",journal.consume(desired(4,3),target)==3 && target.writes==writes);

            // Simulate a process dying after native save but before the applied marker.
            JsonObject wal=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            wal.getAsJsonArray("pending").add(OpSyncJournal.json(desired(5,4)));
            Files.writeString(file,wal.toString());target.level=4;
            journal=new OpSyncJournal(file);journal.recover(target);
            check("crash after native save commits without repeat mutation",target.writes==writes && !journal.hasPending());
            target.level=1;journal=new OpSyncJournal(file);
            check("post-recovery local edit survives another restart",journal.consume(desired(5,4),target)==1 && target.writes==writes);
            JsonObject legacy=new JsonObject();legacy.addProperty("schema",1);JsonArray consumed=new JsonArray();
            consumed.add(OpSyncJournal.json(desired(5,4)));legacy.add("applied",consumed);Files.writeString(file,legacy.toString());
            journal=new OpSyncJournal(file);
            check("legacy ledger keeps consumed version and local edit",journal.consume(desired(5,4),target)==1 && target.writes==writes);
            journal.consume(desired(6,2),target);writes=target.writes;
            check("next event upgrades legacy ledger to write-ahead format",JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("schema").getAsInt()==2);

            Path blocked=dir.resolve("blocked");OpSyncJournal unwritable=new OpSyncJournal(blocked.resolve("journal.json"));
            Files.writeString(blocked,"synthetic file replacing parent directory");
            try {unwritable.consume(desired(1,4),target);throw new AssertionError("journal failure");}
            catch(IOException expected) {check("write-ahead failure does not touch native ops",target.writes==writes);}
            Files.writeString(file,"{bad");
            try {new OpSyncJournal(file);throw new AssertionError("corrupt journal");}
            catch(IOException expected) {passed++;}
            JsonObject row=OpSyncJournal.json(desired(1,4));row.addProperty("revision",Long.MAX_VALUE+"0");
            rejects(()->OpSyncJournal.parse(row));
            JsonObject wrong=OpSyncJournal.json(desired(1,4));wrong.addProperty("offlineUuid",IdentityRules.offlineUuid("10092").toString());
            rejects(()->OpSyncJournal.parse(wrong));
            JsonObject extra=OpSyncJournal.json(desired(1,4));extra.addProperty("platformAdmin",true);
            rejects(()->OpSyncJournal.parse(extra));
            try(var paths=Files.walk(dir)) {for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);}
            return passed;
        } catch(IOException error) {throw new AssertionError(error);}
    }
}
