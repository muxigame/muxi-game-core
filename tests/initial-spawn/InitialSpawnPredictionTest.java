import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.nbt.*;
import net.muxigame.core.feature.dimensions.initialspawn.InitialSpawnPrediction;

public final class InitialSpawnPredictionTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CompoundTag player(boolean done,boolean pending,boolean book) {
        CompoundTag data=new CompoundTag(),root=new CompoundTag(),persisted=new CompoundTag();
        data.putInt("DataVersion",7);persisted.putBoolean("muxiInitialSurvivalDone",done);
        persisted.putBoolean("muxiInitialSurvivalPending",pending);persisted.putBoolean("muxiFirstJoinBook",book);
        root.put("PlayerPersisted",persisted);data.put("NeoForgeData",root);return data;
    }
    public static void main(String[] args)throws Exception{
        check(InitialSpawnPrediction.existing(player(true,true,false),7),"done wins pending, matching native authority");
        check(!InitialSpawnPrediction.existing(player(false,true,true),7),"pending wins book");
        check(InitialSpawnPrediction.existing(player(false,false,true),7),"legacy book player");
        check(!InitialSpawnPrediction.existing(player(false,false,false),7),"new player");
        check(!InitialSpawnPrediction.existing(player(true,false,true),8),"datafix uncertainty must prepare");
        CompoundTag ownerDone=player(true,false,false);ownerDone.remove("DataVersion");
        check(InitialSpawnPrediction.existingLoadedOwner(ownerDone),"already-fixed owner DONE needs no nested version");
        check(!InitialSpawnPrediction.existing(ownerDone,7),"disk tag still needs current DataVersion");
        CompoundTag ownerBook=player(false,false,true);ownerBook.remove("DataVersion");
        check(InitialSpawnPrediction.existingLoadedOwner(ownerBook),"already-fixed owner book needs no nested version");
        CompoundTag ownerPending=player(false,true,true);ownerPending.remove("DataVersion");
        check(!InitialSpawnPrediction.existingLoadedOwner(ownerPending),"owner pending still prepares");
        CompoundTag forge=player(true,false,true);forge.put("ForgeData",forge.getCompound("NeoForgeData"));forge.remove("NeoForgeData");
        check(!InitialSpawnPrediction.existing(forge,7),"legacy schema uncertainty must prepare");
        Path dir=Files.createTempDirectory("muxi-spawn-prediction-");
        Path file=dir.resolve("private-test.dat"),old=dir.resolve("private-test.dat_old");
        try{
            check(!InitialSpawnPrediction.readExisting(file,7),"missing file");
            NbtIo.writeCompressed(player(true,false,true),file);
            byte[] before=Files.readAllBytes(file);
            check(InitialSpawnPrediction.readExisting(file,7),"saved existing player");
            check(Arrays.equals(before,Files.readAllBytes(file)),"prediction mutated player file");
            NbtIo.writeCompressed(player(false,false,false),file);
            NbtIo.writeCompressed(player(true,false,true),old);
            check(!InitialSpawnPrediction.readExisting(file,7),"primary takes precedence over old backup");
            Files.writeString(file,"corrupt");
            check(!InitialSpawnPrediction.readExisting(file,7),"corrupt primary cannot borrow an existing-player backup marker");
            try(var large=new java.io.RandomAccessFile(file.toFile(),"rw")){large.setLength(8L*1024*1024+1);}
            check(!InitialSpawnPrediction.readExisting(file,7),"oversize primary cannot borrow an existing-player backup marker");
            check(Files.size(file)==8L*1024*1024+1,"prediction changed oversized primary");
            Files.delete(file);
            check(InitialSpawnPrediction.readExisting(file,7),"confirmed missing primary uses native backup hint");
            Files.writeString(file,"corrupt");
            Files.delete(old);
            check(!InitialSpawnPrediction.readExisting(file,7),"corrupt without backup must prepare");
            check(Files.readString(file).equals("corrupt"),"no backup/repair mutation");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(old);Files.deleteIfExists(dir);}
        System.out.println("PASS prediction: native flag precedence, already-fixed owner without nested version, uncertain disk schema/version, missing/corrupt NBT, primary/backup precedence, read-only files");
    }
}
