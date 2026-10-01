package net.muxigame.core.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.rcon.RconConsoleSource;
import net.muxigame.core.feature.identity.IdentityRules;
import net.muxigame.core.feature.identity.OpCommandOrigin;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=CommandSourceStack.class, remap=false)
public abstract class OpCommandSourceMixin implements OpCommandOrigin {
    @Shadow @Final private CommandSource source;
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private int permissionLevel;
    @Unique private GameProfile muxi$actor;
    @Unique private boolean muxi$console;
    @Unique private boolean muxi$originOp4;

    @Inject(method="<init>", at=@At("RETURN"))
    private void muxi$captureOrigin(CallbackInfo ci) {
        muxi$console = source == server || source instanceof RconConsoleSource;
        if (source instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            var profile=player.getGameProfile();
            if (IdentityRules.validUid(profile.getName()) && IdentityRules.offlineUuid(profile.getName()).equals(profile.getId()))
                muxi$actor=profile;
        }
        muxi$originOp4=permissionLevel >= 4;
    }

    @Override public boolean muxi$mayManageOps() {
        if (!muxi$originOp4) return false;
        if (muxi$console) return true;
        if (muxi$actor == null) return false;
        var entry=server.getPlayerList().getOps().get(muxi$actor);
        return entry != null && entry.getLevel() == 4;
    }

    @Override public void muxi$inheritOrigin(OpCommandOrigin origin) {
        var parent=(OpCommandSourceMixin)(Object)origin;
        muxi$actor=parent.muxi$actor;
        muxi$console=parent.muxi$console;
        muxi$originOp4=parent.muxi$originOp4 && permissionLevel >= 4;
    }

    @Inject(method={"withSource", "withEntity", "withPosition", "withRotation",
        "withCallback(Lnet/minecraft/commands/CommandResultCallback;)Lnet/minecraft/commands/CommandSourceStack;",
        "withCallback(Lnet/minecraft/commands/CommandResultCallback;Ljava/util/function/BinaryOperator;)Lnet/minecraft/commands/CommandSourceStack;",
        "withSuppressedOutput", "withPermission", "withMaximumPermission", "withAnchor", "withLevel",
        "facing", "withSigningContext"}, at=@At("RETURN"))
    private void muxi$carryOrigin(CallbackInfoReturnable<CommandSourceStack> result) {
        ((OpCommandOrigin)result.getReturnValue()).muxi$inheritOrigin(this);
    }
}
