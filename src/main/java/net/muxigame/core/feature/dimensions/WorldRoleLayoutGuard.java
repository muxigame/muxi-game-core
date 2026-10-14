package net.muxigame.core.feature.dimensions;
import java.nio.file.*;
import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
/** Fail before createLevels can generate/save against a half-switched existing save. */
public final class WorldRoleLayoutGuard {
 public static void verify(MinecraftServer server){Path root=server.getWorldPath(LevelResource.ROOT),level=root.resolve("level.dat"),journal=root.resolve(".muxi-world-roles/journal.json");try{
  if(Files.exists(journal)){var receipt=JsonParser.parseString(Files.readString(journal)).getAsJsonObject();if(!receipt.get("phase").getAsString().equals("committed"))throw new IllegalStateException("World-role migration is incomplete; resume or rollback offline");}
  if(Files.isRegularFile(level)){var data=NbtIo.readCompressed(level,NbtAccounter.create(64L*1024*1024)).getCompound("Data");if(data.getCompound("WorldGenSettings").getCompound("dimensions").contains("muxi_game_core:overworld"))throw new IllegalStateException("Existing home/survival save requires the offline world-role migration before this Core can start");}
 WorldRoleState.prime(server,root);
 }catch(java.io.IOException failure){throw new IllegalStateException("Cannot verify existing world-role layout",failure);}}
 private WorldRoleLayoutGuard(){}
}
