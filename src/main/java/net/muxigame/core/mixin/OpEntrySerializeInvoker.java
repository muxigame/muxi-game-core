package net.muxigame.core.mixin;

import com.google.gson.JsonObject;
import net.minecraft.server.players.ServerOpListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=ServerOpListEntry.class,remap=false)
public interface OpEntrySerializeInvoker {
    @Invoker("serialize") void muxi$serializeOp(JsonObject row);
}
