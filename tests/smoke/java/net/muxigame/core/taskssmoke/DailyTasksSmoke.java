package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import net.muxigame.core.feature.tasks.*;
import net.muxigame.core.mixin.PlayerListSaveInvoker;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Test-only mod. Packaged into the isolated lab, NEVER into the shipped core jar or production mods. */
@Mod("muxi_tasks_smoke")
public final class DailyTasksSmoke {
    private final List<String> passed=new ArrayList<>();
    private int ticks;
    private ServerPlayer player;
    private String task,day,inventoryBefore;
    private Path savedFile;
    private CompoundTag savedNbt;
    private static final UUID UUID_QA=UUID.fromString("e0e0554c-6ef6-4fa4-bf25-1efad6fc5f4d");
    public DailyTasksSmoke() { NeoForge.EVENT_BUS.addListener(this::tick); }
    private void check(String name,boolean result) {
        if(!result) throw new AssertionError(name); passed.add(name);
    }
    private DailyTaskState state() { return DailyTaskState.parse(player.getPersistentData().getCompound("PlayerPersisted").getString("muxi_daily_tasks")); }
    private ServerPlayer newPlayer(MinecraftServer server) {
        return newPlayer(server,UUID_QA);
    }
    private ServerPlayer newPlayer(MinecraftServer server,UUID id) {
        var profile=new GameProfile(id,"MuxiTaskQA");
        ServerPlayer p=new ServerPlayer(server,server.overworld(),profile,ClientInformation.createDefault());
        Connection transport=new Connection(PacketFlow.SERVERBOUND);
        // Native PlayerList.save intentionally skips players with no connection. Use an in-memory
        // listener for a logged-in-shaped test fixture; there is no external client or real socket.
        new EmbeddedChannel(transport);
        p.connection=new ServerGamePacketListenerImpl(server,transport,p,CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(Packet<?> packet) { /* Delivery is covered by the codec tests. */ }
        };
        return p;
    }
    private String inventory() { return player.getInventory().items.toString()+":"+player.totalExperience+":"+player.experienceLevel+":"+player.experienceProgress; }
    private void finish(MinecraftServer server,Throwable error) {
        try {
            Map<String,Object> result=new LinkedHashMap<>(); result.put("passed",passed); result.put("success",error==null);
            if(error!=null) { result.put("error",error.toString()); error.printStackTrace(); }
            Files.writeString(Path.of("tasks-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch(Exception e) { e.printStackTrace(); }
        server.halt(false);
    }
    private void tick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer(); ticks++;
        try {
            var feature=DailyTasksFeature.active(server);
            switch(ticks) {
                case 20 -> {
                    check("dedicated server loaded task feature",feature!=null);
                    check("single-player save invoker applied",server.getPlayerList() instanceof PlayerListSaveInvoker);
                    player=newPlayer(server); feature.request(player,false);
                    DailyTaskState state=state(); check("new player gets three zero-progress tasks",state.entries().size()==3 && state.entries().stream().allMatch(e->e.progress()==0));
                    task=state.entries().get(0).definition.id(); day=state.day().toString();
                    // A real ItemStack with components must survive the exact registered network codec.
                    ItemStack stack=new ItemStack(Items.IRON_SWORD); stack.set(DataComponents.DAMAGE,7);
                    stack.set(DataComponents.CUSTOM_NAME,Component.literal("任务奖励"));
                    stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
                    var row=new TaskNetwork.Row("codec","组件测试","native item tooltip",1,1,"",false,List.of(stack),1,true);
                    var input=new TaskNetwork.Snapshot(day,state.resetAt(),0,List.of(row),false,"",0);
                    var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());
                    try {
                        TaskNetwork.Snapshot.CODEC.encode(buf,input); var output=TaskNetwork.Snapshot.CODEC.decode(buf);
                        ItemStack decoded=output.rows().get(0).rewards().get(0);
                        check("wire codec preserves item components and count",ItemStack.matches(stack,decoded));
                        check("wire codec preserves durability and glint",decoded.getDamageValue()==7 && decoded.hasFoil());
                        check("wire v2 preserves level/hard/quota",output.rows().get(0).experienceLevels()==1 && output.rows().get(0).hard() && output.rerollsRemaining()==0);
                    } finally { buf.release(); }
                    for(var entry:state.entries()) {
                        var d=entry.definition; ResourceLocation id=ResourceLocation.parse(d.targets().get(0)); int count=d.goal()*d.divisor();
                        switch(d.kind()) {
                            case MINED -> player.getStats().setValue(player,Stats.BLOCK_MINED.get(BuiltInRegistries.BLOCK.get(id)),count);
                            case CRAFTED -> player.getStats().setValue(player,Stats.ITEM_CRAFTED.get(BuiltInRegistries.ITEM.get(id)),count);
                            case KILLED -> player.getStats().setValue(player,Stats.ENTITY_KILLED.get(BuiltInRegistries.ENTITY_TYPE.get(id)),count);
                            case CUSTOM -> player.getStats().setValue(player,Stats.CUSTOM.get(BuiltInRegistries.CUSTOM_STAT.get(id)),count);
                        }
                    }
                }
                case 30 -> {
                    feature.request(player,false);
                    check("real server stats complete assigned tasks",state().entries().stream().allMatch(DailyTaskState.Entry::ready));
                }
                case 40 -> {
                    feature.claim(player,"2000-01-01",task);
                    check("stale client date cannot claim",!state().find(task).claimed());
                    for(int i=0;i<player.getInventory().items.size();i++) player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
                    inventoryBefore=inventory();
                }
                case 50 -> {
                    feature.claim(player,day,task);
                    check("full inventory does not consume claim",!state().find(task).claimed());
                    check("full inventory does not partially grant",inventoryBefore.equals(inventory()));
                    player.getInventory().setItem(0,ItemStack.EMPTY); player.getInventory().setItem(1,ItemStack.EMPTY);
                    player.experienceLevel=47; player.experienceProgress=0.625f;
                }
                case 60 -> {
                    feature.claim(player,day,task);
                    check("valid claim becomes claimed",state().find(task).claimed());
                    check("reward appears in inventory",!player.getInventory().getItem(0).isEmpty());
                    check("reward adds exactly one level at level 47",player.experienceLevel==48);
                    check("level reward preserves fractional bar",player.experienceProgress==0.625f);
                    inventoryBefore=inventory();
                    savedFile=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(UUID_QA+".dat");
                    check("claim immediately saves player file",Files.isRegularFile(savedFile));
                    savedNbt=NbtIo.readCompressed(savedFile,NbtAccounter.unlimitedHeap());
                    String json=savedNbt.getCompound("NeoForgeData").getCompound("PlayerPersisted").getString("muxi_daily_tasks");
                    check("claim and inventory share one player save",DailyTaskState.parse(json).find(task).claimed() && !savedNbt.getList("Inventory",10).isEmpty());
                }
                case 70 -> {
                    feature.claim(player,day,task);
                    check("duplicate real claim changes neither items nor XP",inventoryBefore.equals(inventory()));
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    player=newPlayer(server); player.load(savedNbt);
                }
                case 80 -> {
                    feature.request(player,false);
                    check("native NBT reload preserves claim",state().find(task).claimed());
                    check("native NBT reload preserves inventory and XP",inventoryBefore.equals(inventory()));
                    ServerPlayer clone=newPlayer(server);
                    NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone,player,true));
                    check("death clone copies task state",clone.getPersistentData().getCompound("PlayerPersisted").getString("muxi_daily_tasks").equals(state().json()));
                }
                case 90 -> {
                    feature.claim(player,day,task);
                    check("relogin cannot claim again",inventoryBefore.equals(inventory()));
                    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    player=newPlayer(server,UUID.fromString("051e8ab0-af83-4949-9f61-0a9e69cde552"));
                }
                case 100 -> {
                    feature.request(player,false); day=state().day().toString(); task=state().entries().get(0).definition.id();
                    inventoryBefore=inventory();
                }
                case 110 -> {
                    feature.reroll(player,day,task);
                    check("real server replaces one task",state().find(task)==null && state().rerollsRemaining()==0);
                    check("replacement gives no items or levels",inventoryBefore.equals(inventory()));
                    check("replacement remembers discarded task",state().offeredIds().contains(task));
                    savedFile=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID()+".dat");
                    savedNbt=NbtIo.readCompressed(savedFile,NbtAccounter.unlimitedHeap());
                    String json=savedNbt.getCompound("NeoForgeData").getCompound("PlayerPersisted").getString("muxi_daily_tasks");
                    check("replacement allowance saved immediately",DailyTaskState.parse(json).rerollsRemaining()==0);
                    task=state().entries().get(0).definition.id();
                }
                case 120 -> {
                    feature.reroll(player,day,task);
                    check("second server replacement rejected",state().find(task)!=null && state().rerollsRemaining()==0);
                    UUID id=player.getUUID(); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    player=newPlayer(server,id); player.load(savedNbt);
                }
                case 130 -> {
                    feature.request(player,false);
                    check("relogin cannot restore free replacement",state().rerollsRemaining()==0 && state().find(task)!=null);
                    var old=com.google.gson.JsonParser.parseString(state().json()).getAsJsonObject();
                    old.addProperty("day",state().day().minusDays(1).toString());
                    old.addProperty("resetAt",state().resetAt()-86400);
                    UUID id=player.getUUID(); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                    player=newPlayer(server,id);
                    CompoundTag root=new CompoundTag(); root.putString("muxi_daily_tasks",old.toString());
                    player.getPersistentData().put("PlayerPersisted",root);
                    inventoryBefore=old.toString();
                }
                case 140 -> {
                    feature.request(player,false);
                    var yesterday=DailyTaskState.parse(inventoryBefore);
                    check("daily refresh avoids all yesterday offers",Collections.disjoint(state().offeredIds(),yesterday.offeredIds()));
                    check("daily refresh restores one shared replacement",state().rerollsRemaining()==1);
                    finish(server,null);
                }
                default -> {}
            }
        } catch(Throwable e) { finish(server,e); }
    }
}
