package net.muxigame.core.taskssmoke;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.muxigame.core.feature.dimensions.*;
import net.muxigame.core.feature.challenge.ChallengeInventory;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.nio.file.*;
import java.util.*;

@Mod("muxi_tasks_smoke")
public final class DimensionsSmoke {
    private final List<String> passed = new ArrayList<>();
    private int ticks;
    private boolean finished;
    public DimensionsSmoke() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        try { Files.writeString(Path.of("dimensions-server-stopped"), "stopped"); }
        catch(Exception error) { error.printStackTrace(); }
    }
    private void check(String name, boolean ok) { if (!ok) throw new AssertionError(name); passed.add(name); }
    private static void pad(ServerLevel level, BlockPos feet) {
        for (int x=-2;x<=2;x++) for (int z=-2;z<=2;z++) {
            level.setBlockAndUpdate(feet.offset(x,-1,z), Blocks.STONE.defaultBlockState());
            for (int y=0;y<=2;y++) level.setBlockAndUpdate(feet.offset(x,y,z), Blocks.AIR.defaultBlockState());
        }
    }
    @SuppressWarnings("unchecked")
    private ServerPlayer player(MinecraftServer server, GameProfile profile) throws Exception {
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        Connection transport = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(transport);
        player.connection = new ServerGamePacketListenerImpl(server, transport, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };
        var players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
        ((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        var ids = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
        ((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(), player);
        server.overworld().addNewPlayer(player);
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        persisted.putBoolean("muxiFirstJoinBook", true);
        player.getPersistentData().put("PlayerPersisted", persisted);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        return player;
    }
    @SuppressWarnings("unchecked")
    private ServerPlayer newcomer(MinecraftServer server, GameProfile profile) throws Exception {
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        Connection transport = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(transport);
        player.connection = new ServerGamePacketListenerImpl(server, transport, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };
        var players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
        ((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        var ids = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
        ((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(), player);
        server.overworld().addNewPlayer(player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        return player;
    }
    private int command(ServerPlayer p, String arg) throws Exception {
        return p.server.getCommands().getDispatcher().execute(("muxiworld " + arg).strip(), p.createCommandSourceStack().withPermission(2));
    }
    private void ready(ServerPlayer p) { p.getPersistentData().remove("muxi_dimension_travel_tick"); }
    private void exercise(MinecraftServer server) throws Exception {
        var home = server.overworld();
        var world = server.getLevel(WorldDimensions.OVERWORLD);
        var adventure = server.getLevel(WorldDimensions.ADVENTURE);
        check("home retains minecraft:overworld identity", home.dimension().equals(Level.OVERWORLD));
        check("home display name", WorldDimensions.name(home.dimension()).equals("家园"));
        check("survival dimension loaded", world != null && world != home);
        check("adventure dimension loaded", adventure != null && adventure != home && adventure != world);
        check("adventure display name", WorldDimensions.name(adventure.dimension()).equals("冒险世界"));
        check("both custom worlds inherit overworld behavior", WorldDimensions.exploration(world.dimension()) && WorldDimensions.exploration(adventure.dimension()));
        check("only survival world is resettable", WorldDimensions.resettable(world.dimension()) && !WorldDimensions.resettable(adventure.dimension()) && !WorldDimensions.resettable(home.dimension()));
        check("eternal night not registered",server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse("muxi_game_core:eternal_night")))==null);
        check("unrelated Nether and End retained", server.getLevel(Level.NETHER) != null && server.getLevel(Level.END) != null);
        check("new overworld has normal time", !world.dimensionType().hasFixedTime());
        check("adventure has normal time", !adventure.dimensionType().hasFixedTime());
        check("survival and adventure share the world seed", world.getSeed()==adventure.getSeed() && world.getSeed()==home.getSeed());
        check("survival and adventure use the same generator kind", world.getChunkSource().getGenerator().getClass().equals(adventure.getChunkSource().getGenerator().getClass()));
        BlockPos base = new BlockPos(0, 180, 0);
        for (var level : List.of(home, world, adventure)) { pad(level, base); level.setDefaultSpawnPos(base, 0); }
        home.setBlockAndUpdate(base.offset(4,0,0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        check("generated normal terrain in new world", !world.getBlockState(new BlockPos(0,-60,0)).isAir());
        check("generated normal terrain in adventure world", !adventure.getBlockState(new BlockPos(0,-60,0)).isAir());
        var newProfile = new GameProfile(UUID.randomUUID(), "FirstJoinQA");
        ServerPlayer newcomer = newcomer(server, newProfile);
        check("new player is marked for initial survival spawn",
            DimensionsFeature.needsInitialSurvival(newcomer));
        BlockPos initial = DimensionsFeature.findInitialSurvivalLanding(world,newcomer);
        check("random initial survival landing exists", initial != null);
        check("random initial survival landing is safe", DimensionsFeature.safeInitialSpawn(world,initial));
        long spawnDistance = (long)initial.getX() * initial.getX() + (long)initial.getZ() * initial.getZ();
        check("new player spawn is within 10000-block radius", spawnDistance <= 10000L * 10000L);
        check("new player spawn is not ocean", !world.getBiome(initial).is(net.minecraft.tags.BiomeTags.IS_OCEAN));
        check("new player spawn has two-block air headroom", world.isEmptyBlock(initial) && world.isEmptyBlock(initial.above()));
        check("new player spawn has no fluid", world.getFluidState(initial).isEmpty() && world.getFluidState(initial.above()).isEmpty());
        newcomer.setServerLevel(world);
        DimensionsFeature.applyInitialSurvivalSpawn(newcomer,initial);
        check("new player receives survival position", newcomer.level() == world && newcomer.blockPosition().equals(initial));
        check("new player initial respawn stays in survival", newcomer.getRespawnDimension().equals(WorldDimensions.OVERWORLD));
        check("new player initial respawn position matches landing", newcomer.getRespawnPosition().equals(newcomer.blockPosition()));
        check("successful onboarding is one-shot",
            newcomer.getPersistentData().getCompound("PlayerPersisted").getBoolean("muxiInitialSurvivalDone")
                && !newcomer.getPersistentData().getCompound("PlayerPersisted").getBoolean("muxiInitialSurvivalPending")
                && !DimensionsFeature.needsInitialSurvival(newcomer));
        var profile = new GameProfile(UUID.randomUUID(), "DimensionQA");
        ServerPlayer p = player(server, profile); p.setPos(0.5,180,0.5);
        check("existing player is not migrated by new onboarding",
            !p.getPersistentData().getCompound("PlayerPersisted").getBoolean("muxiInitialSurvivalPending")
                && !DimensionsFeature.needsInitialSurvival(p));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 23)); p.giveExperienceLevels(7);
        check("admin can see debug menu", command(p, "") == 1);
        check("unknown world denied", throwsCommand(p, "quarantine"));
        check("admin debug travel enters survival", command(p, "overworld") == 1 && p.level() == world);
        check("inventory and xp shared", p.getInventory().getItem(0).getCount() == 23 && p.experienceLevel == 7);
        check("safe arrival with solid floor", DimensionsFeature.safe(world, p.blockPosition()));
        check("cooldown stops repeated travel", command(p, "home") == 0 && p.level() == world);
        ready(p);
        check("returns to original home", command(p, "home") == 1 && p.level() == home && p.blockPosition().equals(base));
        check("same dimension rejected", command(p,"home")==0);
        ready(p);
        check("admin debug travel enters adventure", command(p, "adventure") == 1 && p.level() == adventure);
        check("inventory and xp shared with adventure", p.getInventory().getItem(0).getCount() == 23 && p.experienceLevel == 7);
        ready(p);
        check("adventure returns home", command(p, "home") == 1 && p.level() == home);
        check("existing home blocks unchanged", home.getBlockState(base.offset(4,0,0)).is(Blocks.DIAMOND_BLOCK));
        ready(p); p.getPersistentData().put(ChallengeInventory.KEY, new CompoundTag());
        check("challenge return snapshot blocks escape", command(p, "overworld") == 0 && p.level() == home);
        p.getPersistentData().remove(ChallengeInventory.KEY);
        p.inventoryMenu.setCarried(new ItemStack(Items.EMERALD));
        check("cursor item blocks travel", command(p, "overworld") == 0 && p.level() == home);
        p.inventoryMenu.setCarried(ItemStack.EMPTY);
        check("safe saved underground point preserved", DimensionsFeature.findLanding(home, base).equals(base));
        for (var block : List.of(Blocks.MAGMA_BLOCK, Blocks.CACTUS, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE)) {
            home.setBlockAndUpdate(base.below(), block.defaultBlockState());
            check("reject dangerous floor " + block, !DimensionsFeature.safe(home, base));
        }
        pad(home, base);
        for (var block : List.of(Blocks.LAVA, Blocks.WATER, Blocks.POWDER_SNOW, Blocks.FIRE)) {
            home.setBlockAndUpdate(base, block.defaultBlockState());
            check("reject dangerous feet " + block, !DimensionsFeature.safe(home, base));
        }
        pad(home, base);
        home.setBlockAndUpdate(base.above(), Blocks.STONE.defaultBlockState());
        check("blocked headroom gets another landing", !DimensionsFeature.safe(home, base) && !base.equals(DimensionsFeature.findLanding(home, base)));
        pad(home, base);
        world.getWorldBorder().setSize(1);
        check("outside border rejected", !DimensionsFeature.safe(world, base.offset(3,0,0)));
        world.getWorldBorder().setSize(59999968);
        // Use vanilla player serialization, then clone event as happens on death.
        CompoundTag saved = new CompoundTag(); p.saveWithoutId(saved);
        ServerPlayer loaded = new ServerPlayer(server, home, profile, ClientInformation.createDefault()); loaded.load(saved);
        check("return locations survive player serialization", loaded.getPersistentData().getCompound(DimensionsFeature.POSITIONS).equals(p.getPersistentData().getCompound(DimensionsFeature.POSITIONS)));
        ServerPlayer clone = new ServerPlayer(server, home, profile, ClientInformation.createDefault());
        clone.connection=p.connection;
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, p, true));
        check("return locations survive death clone", clone.getPersistentData().getCompound(DimensionsFeature.POSITIONS).equals(p.getPersistentData().getCompound(DimensionsFeature.POSITIONS)));
        server.saveEverything(false, true, true);
        Path root = server.getWorldPath(LevelResource.ROOT);
        check("old overworld remains in region folder", Files.isDirectory(root.resolve("region")));
        check("new world uses separate region folder", Files.isDirectory(root.resolve("dimensions/muxi_game_core/overworld/region")));
        check("adventure uses separate persistent region folder", Files.isDirectory(root.resolve("dimensions/muxi_game_core/adventure/region")));
    }
    private boolean throwsCommand(ServerPlayer player, String command) {
        try { command(player, command); return false; } catch (Exception expected) { return true; }
    }
    private void tick(ServerTickEvent.Post event) {
        if (finished || ++ticks < 20) return;
        finished = true;
        var result = new LinkedHashMap<String,Object>();
        try { exercise(event.getServer()); result.put("success", true); }
        catch (Throwable error) { error.printStackTrace(); result.put("success", false); result.put("error", error.toString()); }
        result.put("passed", passed);
        try { Files.writeString(Path.of("tasks-smoke-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        event.getServer().halt(false);
    }
}
