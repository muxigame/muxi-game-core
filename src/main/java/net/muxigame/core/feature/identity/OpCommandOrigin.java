package net.muxigame.core.feature.identity;

import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Immutable originating authority, carried through every vanilla source transformation. */
public interface OpCommandOrigin {
    boolean muxi$mayManageOps();
    void muxi$inheritOrigin(OpCommandOrigin origin);

    static boolean allowed(CommandSourceStack source) {
        return source.hasPermission(4) && source instanceof OpCommandOrigin origin && origin.muxi$mayManageOps();
    }

    static void require(CommandSourceStack source) throws CommandSyntaxException {
        if (!allowed(source)) throw new SimpleCommandExceptionType(
            Component.literal("只有 OP4 玩家或可信服务端控制台可管理游戏 OP。")).create();
        GameOpSync.requireLocalMutationReady(source.getServer());
    }
}
