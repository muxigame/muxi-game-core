import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import dev.ryanhcode.sable.sublevel.storage.region.*;
import dev.ryanhcode.sable.sublevel.storage.holding.*;
import dev.ryanhcode.sable.sublevel.storage.serialization.*;
import dev.ryanhcode.sable.companion.math.*;
import dev.ryanhcode.sable.util.SableNBTUtils;
public final class SableStorageCodecQA {
 static final UUID ID=UUID.fromString("12345678-1234-5678-9abc-def012345678"), DEP=UUID.fromString("87654321-4321-8765-abcd-123456789abc");
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static CompoundTag structure(String dimension,boolean large){
  var tag=new CompoundTag();tag.putUUID("uuid",ID);var plot=new CompoundTag();plot.putInt("plot_x",37);plot.putInt("plot_z",-8);plot.putInt("log_size",4);plot.putInt("data_version",1);plot.putString("biome","minecraft:plains");var chunks=new CompoundTag();var chunk=new CompoundTag();var sections=new ListTag();var section=new CompoundTag();section.putByte("Y",(byte)4);var states=new CompoundTag();var palette=new ListTag();var block=new CompoundTag();block.putString("Name","minecraft:oak_planks");palette.add(block);states.put("palette",palette);states.putLongArray("data",new long[]{0L,Long.MAX_VALUE,-1L});section.put("block_states",states);sections.add(section);chunk.put("sections",sections);var entities=new ListTag();var entity=new CompoundTag();entity.putUUID("UUID",DEP);entity.putFloat("Health",27.5f);entity.putString("id","minecraft:pig");entities.add(entity);chunk.put("entities",entities);chunks.put("0",chunk);plot.put("chunks",chunks);tag.put("plot",plot);tag.put("pose",SableNBTUtils.writePose3d(new Pose3d()));tag.put("world_bounds",SableNBTUtils.writeBoundingBox(new BoundingBox3d(-12.5,70.25,-3.0,18.75,89.5,5.0)));
  var dependencies=new ListTag();dependencies.add(NbtUtils.createUUID(DEP));tag.put("loading_dependencies",dependencies);tag.putString("display_name","minecraft:overworld");
  var user=new CompoundTag();user.putString("dimension",dimension);user.putString("type","minecraft:overworld");user.putIntArray("block_palette",new int[]{1,7,31,65535});user.putLongArray("block_states",new long[]{-1L,0x123456789abcdefL,Long.MAX_VALUE});user.putFloat("entity_health",27.5f);
  if(large){byte[] bytes=new byte[70000];new Random(100).nextBytes(bytes);user.putByteArray("voxel_payload",bytes);}tag.put("user_data",user);return tag;
 }
 static CompoundTag holding(String dimension,boolean large){
  var holder=new SubLevelHoldingChunk(new ChunkPos(0,0));holder.getSubLevelPointers().add(new SavedSubLevelPointer((short)0,(short)3));holder.getSubLevelPointers().add(new SavedSubLevelPointer((short)0,(short)4));var tag=new CompoundTag();holder.writeTo(tag);tag.put("user_data",structure(dimension,large).getCompound("user_data"));return tag;
 }
 static void create(Path world)throws Exception{
  for(boolean home:new boolean[]{true,false}){Path folder=world.resolve(home?"sublevels":"dimensions/muxi_game_core/overworld/sublevels");Files.createDirectories(folder);Path external=folder.resolve("r.0.0.r");Files.createDirectories(external);String dimension=home?"minecraft:overworld":"muxi_game_core:overworld";
   try(var region=new SubLevelRegionFile(folder.resolve("r.0.0.slvlr"),external)){region.write(0,holding(dimension,false));region.write(1,holding(dimension,true));}
   try(var data=new SubLevelStorageFile(folder.resolve("r.0.0.0.slvls"),external)){data.write(3,structure(dimension,false));var buddy=structure(dimension,false);buddy.putUUID("uuid",DEP);buddy.put("loading_dependencies",new ListTag());data.write(4,buddy);}
  }
 }
 static void verify(Path world,boolean migrated)throws Exception{
  for(boolean home:new boolean[]{true,false}){Path folder=world.resolve(home?(migrated?"dimensions/muxi_game_core/homeworld/sublevels":"sublevels"):(migrated?"sublevels":"dimensions/muxi_game_core/overworld/sublevels"));Path external=folder.resolve("r.0.0.r");Files.createDirectories(external);String dimension=home?(migrated?"muxi_game_core:homeworld":"minecraft:overworld"):(migrated?"minecraft:overworld":"muxi_game_core:overworld");
   try(var region=new SubLevelRegionFile(folder.resolve("r.0.0.slvlr"),external)){for(int slot:new int[]{0,1}){var tag=region.read(slot);check(tag!=null,"native holding container readable");var holder=SubLevelHoldingChunk.from(new ChunkPos(0,0),tag);check(holder.getSubLevelPointers().equals(List.of(new SavedSubLevelPointer((short)0,(short)3),new SavedSubLevelPointer((short)0,(short)4))),"native construct storage/index pointers retained");check(tag.getCompound("user_data").equals(holding(dimension,slot==1).getCompound("user_data")),"native holding voxel/palette/state/health/dimension exactly retained");}}
   try(var data=new SubLevelStorageFile(folder.resolve("r.0.0.0.slvls"),external)){var tag=data.read(3);var restored=SubLevelSerializer.fromData(tag);check(restored.uuid().equals(ID),"native construct UUID retained");check(restored.dependencies().equals(List.of(DEP)),"native construct loading dependency retained");check(tag.equals(structure(dimension,false)),"native full construct tag retains pose/bounds/user data/name except reviewed dimension");var buddy=data.read(4);var expected=structure(dimension,false);expected.putUUID("uuid",DEP);expected.put("loading_dependencies",new ListTag());check(buddy.equals(expected),"native dependency target construct retained");}
   try(var storage=new SubLevelStorage(folder)){check(storage.attemptLoadSubLevel(new ChunkPos(0,0),new SavedSubLevelPointer((short)0,(short)3)).uuid().equals(ID),"native holding pointer resolves original construct");check(storage.attemptLoadSubLevel(new ChunkPos(0,0),new SavedSubLevelPointer((short)0,(short)4)).uuid().equals(DEP),"native holding pointer resolves existing dependency target");}
  }
 }
 public static void main(String[] args)throws Exception{Path world=Path.of(args[1]);if(args[0].equals("create"))create(world);else verify(world,args[0].equals("verify-migrated"));System.out.println("{\"success\":true,\"checks\":"+checks+",\"minecraftStarted\":false,\"nativeContainerClasses\":true}");}
}
