package net.muxigame.core.feature.identity;

import com.google.gson.*;
import net.minecraft.server.players.ServerOpList;
import net.muxigame.core.mixin.OpEntrySerializeInvoker;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Preserve native entry serialization while avoiding a truncated ops.json after a crash. */
public final class OpListPersistence {
    private OpListPersistence() {}
    public static void save(ServerOpList ops) throws IOException {
        JsonArray entries=new JsonArray();
        for(var entry:ops.getEntries()) {
            JsonObject row=new JsonObject();
            ((OpEntrySerializeInvoker)(Object)entry).muxi$serializeOp(row);
            entries.add(row);
        }
        Path file=ops.getFile().toPath().toAbsolutePath();
        Path temporary=Files.createTempFile(file.getParent(),"native-ops-",".tmp");
        try {
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer data=StandardCharsets.UTF_8.encode(new GsonBuilder().setPrettyPrinting().create().toJson(entries));
                while(data.hasRemaining())channel.write(data);
                channel.force(true);
            }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }
}
