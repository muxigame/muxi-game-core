package net.muxigame.core.client.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.muxigame.core.feature.tasks.TaskNetwork;

/**
 * Small client-side API consumed reflectively by muxi-terminal.
 *
 * <p>The task system remains owned by Game Core: network state, claim/reroll
 * validation and HUD tracking all continue to use DailyTasksClient and its
 * TaskHudSettings. The terminal is only another presentation surface.</p>
 */
public final class TerminalTasksApi {
    private TerminalTasksApi() {}

    public static String snapshotJson() {
        JsonObject root=new JsonObject();
        root.addProperty("supported",DailyTasksClient.supported());
        JsonArray mainline=new JsonArray();
        for(MainlineTasks.Entry entry:MainlineTasks.CURRENT){
            JsonObject item=new JsonObject();
            item.addProperty("title",entry.title());
            item.addProperty("description",entry.description());
            mainline.add(item);
        }
        root.add("mainline",mainline);
        TaskNetwork.Snapshot snapshot=DailyTasksClient.snapshot;
        if(snapshot==null) {
            root.addProperty("loading",true);
            root.add("rows",new JsonArray());
            return root.toString();
        }

        root.addProperty("loading",false);
        root.addProperty("day",snapshot.day());
        root.addProperty("remaining",DailyTasksClient.remaining());
        root.addProperty("rerollsRemaining",snapshot.rerollsRemaining());
        root.addProperty("notice",snapshot.notice());
        JsonArray rows=new JsonArray();
        for(TaskNetwork.Row row:snapshot.rows()) rows.add(rowJson(row));
        root.add("rows",rows);
        return root.toString();
    }

    private static JsonObject rowJson(TaskNetwork.Row row) {
        JsonObject out=new JsonObject();
        out.addProperty("id",row.id());
        out.addProperty("title",row.title());
        out.addProperty("description",row.description());
        out.addProperty("progress",row.progress());
        out.addProperty("goal",row.goal());
        out.addProperty("unit",row.unit());
        out.addProperty("claimed",row.claimed());
        out.addProperty("ready",row.ready());
        out.addProperty("hard",row.hard());
        out.addProperty("tracked",DailyTasksClient.settings.isTracked(row.id()));
        out.addProperty("rerollable",DailyTasksClient.rerollable(row));
        out.addProperty("experienceLevels",row.experienceLevels());
        JsonArray rewards=new JsonArray();
        for(ItemStack stack:row.rewards()) {
            JsonObject reward=new JsonObject();
            var key=BuiltInRegistries.ITEM.getKey(stack.getItem());
            reward.addProperty("item",key.toString());
            reward.addProperty("name",stack.getHoverName().getString());
            reward.addProperty("count",stack.getCount());
            rewards.add(reward);
        }
        out.add("rewards",rewards);
        return out;
    }

    public static void request() { DailyTasksClient.request(); }

    public static void claim(String id) {
        TaskNetwork.Row row=find(id);
        if(row!=null) DailyTasksClient.claim(row);
    }

    public static void reroll(String id) {
        TaskNetwork.Snapshot snapshot=DailyTasksClient.snapshot;
        if(snapshot!=null) DailyTasksClient.reroll(snapshot.day(),id);
    }

    public static void toggleTracked(String id) {
        if(find(id)!=null) DailyTasksClient.settings.toggleTracked(id);
    }

    private static TaskNetwork.Row find(String id) {
        TaskNetwork.Snapshot snapshot=DailyTasksClient.snapshot;
        if(snapshot==null || id==null) return null;
        return snapshot.rows().stream().filter(row->row.id().equals(id)).findFirst().orElse(null);
    }
}
