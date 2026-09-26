package net.muxigame.core.feature.tasks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

/** Real ownership only: maid, Modular Golems and vanilla pets implement OwnableEntity. */
public final class TaskOwnership {
    private TaskOwnership() {}
    public static ServerPlayer credit(DamageSource source) {
        ServerPlayer owner=credit(source.getEntity());
        return owner!=null?owner:credit(source.getDirectEntity());
    }
    public static ServerPlayer credit(Entity source) {
        Set<Entity> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int depth=0;source!=null && depth<8 && visited.add(source);depth++) {
            if(source.level().isClientSide()) return null;
            if(source instanceof ServerPlayer player) return player instanceof FakePlayer?null:player;
            if(source instanceof Projectile projectile) { source=projectile.getOwner(); continue; }
            if(source instanceof OwnableEntity owned) {
                UUID uuid=owned.getOwnerUUID();
                if(uuid==null || source.getServer()==null) return null;
                ServerPlayer player=source.getServer().getPlayerList().getPlayer(uuid);
                if(player!=null && !(player instanceof FakePlayer)) return player;
                source=owned.getOwner(); continue;
            }
            return null;
        }
        return null;
    }
}
