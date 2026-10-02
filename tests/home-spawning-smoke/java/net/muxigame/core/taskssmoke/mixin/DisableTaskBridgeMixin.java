package net.muxigame.core.taskssmoke.mixin;

import net.muxigame.core.feature.tasks.DailyTasksFeature;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This spawning-only lab excludes the unrelated account points HTTP startup. Never shipped in Core. */
@Mixin(value=DailyTasksFeature.class,remap=false)
public abstract class DisableTaskBridgeMixin {
    @Inject(method="onStarted",at=@At("HEAD"),cancellable=true)
    private void muxi$skipAccountBridge(ServerStartedEvent event, CallbackInfo callback) { callback.cancel(); }
}
