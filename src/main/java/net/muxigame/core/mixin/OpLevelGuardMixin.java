package net.muxigame.core.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.muxigame.core.feature.identity.OpCommandOrigin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

/** Handler boundary also covers redirected aliases and function/execute invocation. */
@Mixin(targets="net.minecraft.server.commands.OpCommand", remap=false)
public abstract class OpLevelGuardMixin {
    @Inject(method="opPlayers",at=@At("HEAD"),cancellable=true)
    private static void muxi$requireOp4(CommandSourceStack source, Collection<GameProfile> targets,
                                      CallbackInfoReturnable<Integer> result) throws CommandSyntaxException {
        OpCommandOrigin.require(source);
    }
}
