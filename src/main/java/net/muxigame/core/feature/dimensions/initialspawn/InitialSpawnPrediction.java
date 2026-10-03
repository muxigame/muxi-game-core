package net.muxigame.core.feature.dimensions.initialspawn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Preparation hint only. The original PlayerList.load and its events remain authoritative. */
public final class InitialSpawnPrediction {
    private static final long MAX_COMPRESSED_BYTES = 8L * 1024 * 1024;
    private static final long MAX_NBT_BYTES = 16L * 1024 * 1024;
    private InitialSpawnPrediction() {}

    public static boolean existing(CompoundTag data, int currentVersion) {
        return data != null && data.contains("DataVersion", Tag.TAG_ANY_NUMERIC)
            && data.getInt("DataVersion") == currentVersion && existingLoadedOwner(data);
    }

    /** Only for WorldData's already-datafixed owner Player tag, which has no nested DataVersion. */
    public static boolean existingLoadedOwner(CompoundTag data) {
        if (data == null || !data.contains("NeoForgeData", Tag.TAG_COMPOUND)) return false;
        CompoundTag root = data.getCompound("NeoForgeData");
        if (!root.contains("PlayerPersisted", Tag.TAG_COMPOUND)) return false;
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        if (persisted.getBoolean("muxiInitialSurvivalDone")) return true;
        if (persisted.getBoolean("muxiInitialSurvivalPending")) return false;
        return persisted.getBoolean("muxiFirstJoinBook");
    }

    /** Worker may read only these paths; no player/world objects, datafix, backups or load events. */
    public static boolean readExisting(Path playerFile, int currentVersion) {
        try {
            // A capped/failed read is not evidence that native load will fall back to .dat_old.
            // Only a confirmed missing primary may use the backup as an existing-player hint.
            if (!Files.notExists(playerFile)) return existing(read(playerFile), currentVersion);
            return existing(read(playerFile.resolveSibling(playerFile.getFileName() + "_old")), currentVersion);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static CompoundTag read(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_COMPRESSED_BYTES) return null;
            return NbtIo.readCompressed(path, NbtAccounter.create(MAX_NBT_BYTES));
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }
}
