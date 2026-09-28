package net.muxigame.core.feature.challenge;

import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.LogicalSide;

/** Server-side lethal bullet metadata, correlated with an uncanceled actual death by the service. */
public final class ChallengeHeadshots {
    public ChallengeHeadshots(ChallengeFeature feature,IEventBus bus){
        bus.addListener((EntityKillByGunEvent e)->{
            if(e.getLogicalSide()==LogicalSide.SERVER && e.isHeadShot() && e.getAttacker() instanceof ServerPlayer p)
                feature.headshot(p,e.getKilledEntity());
        });
    }
}
