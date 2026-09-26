package net.muxigame.core.feature.tasks;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;
import java.util.function.Consumer;

/** Optional play-phase protocol. Common classes never reference Minecraft client classes. */
public final class TaskNetwork {
    private TaskNetwork() {}
    private static volatile Consumer<Snapshot> clientReceiver = ignored -> {};
    public static void clientReceiver(Consumer<Snapshot> receiver) { clientReceiver=receiver; }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("muxi_game_core", name); }
    public record Row(String id, String title, String description, int progress, int goal, String unit,
                      boolean claimed, List<ItemStack> rewards, int experienceLevels, boolean hard) {
        public Row { rewards=List.copyOf(rewards); }
        public Row(String id,String title,String description,int progress,int goal,String unit,boolean claimed,List<ItemStack> rewards,int levels) {
            this(id,title,description,progress,goal,unit,claimed,rewards,levels,false);
        }
        public boolean ready() { return !claimed && progress >= goal; }
    }
    public record Snapshot(String day, long resetAt, long serverTime, List<Row> rows, boolean open, String notice, int rerollsRemaining)
        implements CustomPacketPayload {
        public Snapshot { rows=List.copyOf(rows); }
        public Snapshot(String day,long resetAt,long serverTime,List<Row> rows,boolean open,String notice) {
            this(day,resetAt,serverTime,rows,open,notice,rows.isEmpty()?0:1);
        }
        public static final Type<Snapshot> TYPE = new Type<>(id("daily_tasks_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(Snapshot::write, Snapshot::read);
        @Override public Type<Snapshot> type() { return TYPE; }
        private static void write(RegistryFriendlyByteBuf b, Snapshot s) {
            b.writeUtf(s.day, 16); b.writeLong(s.resetAt); b.writeLong(s.serverTime); b.writeBoolean(s.open);
            b.writeUtf(s.notice, 128); b.writeVarInt(s.rerollsRemaining); b.writeVarInt(s.rows.size());
            for (Row r : s.rows) {
                b.writeUtf(r.id, 48); b.writeUtf(r.title, 64); b.writeUtf(r.description, 256);
                b.writeVarInt(r.progress); b.writeVarInt(r.goal); b.writeUtf(r.unit, 8); b.writeBoolean(r.claimed);
                b.writeVarInt(r.rewards.size());
                for (ItemStack stack : r.rewards) ItemStack.STREAM_CODEC.encode(b, stack);
                b.writeVarInt(r.experienceLevels); b.writeBoolean(r.hard);
            }
        }
        private static Snapshot read(RegistryFriendlyByteBuf b) {
            String day=b.readUtf(16); long reset=b.readLong(), time=b.readLong(); boolean open=b.readBoolean();
            String notice=b.readUtf(128); int rerolls=bounded(b.readVarInt(),0,1); int size=bounded(b.readVarInt(), 0, TaskCatalog.MAX_TASKS);
            List<Row> rows = new ArrayList<>();
            for (int i=0; i<size; i++) {
                String id=b.readUtf(48), title=b.readUtf(64), description=b.readUtf(256);
                int progress=bounded(b.readVarInt(), 0, 1000000), goal=bounded(b.readVarInt(), 1, 1000000);
                String unit=b.readUtf(8); boolean claimed=b.readBoolean();
                int count=bounded(b.readVarInt(), 0, TaskCatalog.MAX_REWARDS); List<ItemStack> rewards=new ArrayList<>();
                for (int j=0; j<count; j++) rewards.add(ItemStack.STREAM_CODEC.decode(b));
                int levels=bounded(b.readVarInt(), 0, 100); boolean hard=b.readBoolean();
                rows.add(new Row(id,title,description,Math.min(progress,goal),goal,unit,claimed,rewards,levels,hard));
            }
            return new Snapshot(day,reset,time,rows,open,notice,rerolls);
        }
    }
    public record Request(boolean open) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(id("daily_tasks_request_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC=StreamCodec.of(
            (b,r)->b.writeBoolean(r.open), b->new Request(b.readBoolean()));
        @Override public Type<Request> type() { return TYPE; }
    }
    /** No UUID, item, quantity, progress, or reward supplied by the client. */
    public record Claim(String day, String taskId) implements CustomPacketPayload {
        public static final Type<Claim> TYPE=new Type<>(id("daily_tasks_claim_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> CODEC=StreamCodec.of(
            (b,c)->{b.writeUtf(c.day,16);b.writeUtf(c.taskId,48);}, b->new Claim(b.readUtf(16),b.readUtf(48)));
        @Override public Type<Claim> type() { return TYPE; }
    }
    /** The server chooses the replacement; the client cannot supply a task pool or new reward. */
    public record Reroll(String day,String taskId) implements CustomPacketPayload {
        public static final Type<Reroll> TYPE=new Type<>(id("daily_tasks_reroll_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reroll> CODEC=StreamCodec.of(
            (b,r)->{b.writeUtf(r.day,16);b.writeUtf(r.taskId,48);},b->new Reroll(b.readUtf(16),b.readUtf(48)));
        @Override public Type<Reroll> type() { return TYPE; }
    }
    public static void register(IEventBus modBus) { modBus.addListener(TaskNetwork::registerPayloads); }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("daily-tasks-2").optional();
        r.playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet,context)->clientReceiver.accept(packet));
        r.playToServer(Request.TYPE, Request.CODEC, (packet,context)-> {
            if (context.player() instanceof ServerPlayer player) {
                DailyTasksFeature feature=DailyTasksFeature.active(player.server);
                if (feature!=null) feature.request(player,packet.open());
            }
        });
        r.playToServer(Claim.TYPE, Claim.CODEC, (packet,context)-> {
            if (context.player() instanceof ServerPlayer player) {
                DailyTasksFeature feature=DailyTasksFeature.active(player.server);
                if (feature!=null) feature.claim(player,packet.day(),packet.taskId());
            }
        });
        r.playToServer(Reroll.TYPE,Reroll.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player) {
                DailyTasksFeature feature=DailyTasksFeature.active(player.server);
                if(feature!=null) feature.reroll(player,packet.day(),packet.taskId());
            }
        });
    }
    public static boolean supported(ServerPlayer player) { return player.connection!=null && NetworkRegistry.hasChannel(player.connection,Snapshot.TYPE.id()); }
    public static void send(ServerPlayer player, Snapshot snapshot) {
        if (supported(player)) PacketDistributor.sendToPlayer(player,snapshot);
    }
    private static int bounded(int n,int min,int max) {
        if(n<min || n>max) throw new DecoderException("Daily task payload out of bounds"); return n;
    }
}
