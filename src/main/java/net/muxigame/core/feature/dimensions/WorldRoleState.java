package net.muxigame.core.feature.dimensions;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Small global role metadata, written by the offline migration. Terrain is never copied here. */
public final class WorldRoleState extends SavedData {
 private static final java.util.Map<MinecraftServer,WorldRoleState> STATES=new java.util.WeakHashMap<>();
 private BlockPos homeSpawn;private float homeAngle;private boolean homeHub;
 private static WorldRoleState load(CompoundTag tag){var state=new WorldRoleState();if(tag.getInt("schema")!=2)throw new IllegalStateException("Unsupported world role schema");if(tag.contains("homeSpawn",Tag.TAG_COMPOUND)){var spawn=tag.getCompound("homeSpawn");state.homeSpawn=new BlockPos(spawn.getInt("x"),spawn.getInt("y"),spawn.getInt("z"));state.homeAngle=spawn.getFloat("angle");}String hub=tag.getString("portalHub");if(!hub.equals("survival")&&!hub.equals("home"))throw new IllegalStateException("World role portal hub has not been resolved");state.homeHub=hub.equals("home");return state;}
 public static void prime(MinecraftServer server,java.nio.file.Path root)throws java.io.IOException {synchronized(STATES){var file=root.resolve("data/muxi_world_roles.dat");var state=java.nio.file.Files.isRegularFile(file)?load(NbtIo.readCompressed(file,NbtAccounter.create(1024L*1024)).getCompound("data")):new WorldRoleState();STATES.put(server,state);}}
 public static WorldRoleState get(MinecraftServer server){synchronized(STATES){var state=STATES.get(server);if(state==null)throw new IllegalStateException("World role metadata must be primed before levels load");return state;}}
 public BlockPos homeSpawn(){return homeSpawn;}public float homeAngle(){return homeAngle;}public boolean homeHub(){return homeHub;}
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putInt("schema",2);tag.putString("portalHub",homeHub?"home":"survival");if(homeSpawn!=null){var spawn=new CompoundTag();spawn.putInt("x",homeSpawn.getX());spawn.putInt("y",homeSpawn.getY());spawn.putInt("z",homeSpawn.getZ());spawn.putFloat("angle",homeAngle);tag.put("homeSpawn",spawn);}return tag;}
}
