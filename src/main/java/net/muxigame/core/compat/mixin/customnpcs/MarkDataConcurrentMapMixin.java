package net.muxigame.core.compat.mixin.customnpcs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** CustomNPCs serializes worldgen entities on C2ME workers, so its global mark cache cannot be a HashMap. */
@Pseudo
@Mixin(targets="noppes.npcs.controllers.data.MarkData",remap=false)
public abstract class MarkDataConcurrentMapMixin {
    @Unique private static final Logger muxi$log=LoggerFactory.getLogger("muxi-game-core/customnpcs");
    @Shadow(remap=false) private static Map<Integer,Object> dataMap;

    @Inject(method="<clinit>",at=@At("TAIL"),remap=false)
    private static void muxi$installConcurrentCache(CallbackInfo ci) {
        dataMap=new ConcurrentHashMap<>();
        muxi$log.info("CustomNPCs MarkData cache made thread-safe for parallel chunk generation");
    }
}
