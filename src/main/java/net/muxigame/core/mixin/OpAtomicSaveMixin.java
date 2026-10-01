package net.muxigame.core.mixin;

import java.io.IOException;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.StoredUserList;
import net.muxigame.core.feature.identity.OpListPersistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=StoredUserList.class,remap=false)
public abstract class OpAtomicSaveMixin {
    @Inject(method="save",at=@At("HEAD"),cancellable=true)
    private void muxi$atomicOpsSave(CallbackInfo result) throws IOException {
        if((Object)this instanceof ServerOpList ops) {
            OpListPersistence.save(ops);
            result.cancel();
        }
    }
}
