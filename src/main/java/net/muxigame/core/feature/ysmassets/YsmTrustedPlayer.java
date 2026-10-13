package net.muxigame.core.feature.ysmassets;

import net.minecraft.server.level.ServerPlayer;
import net.muxigame.minigames.TrustedAccounts;

/** Current server-verified UID only; network inputs contain handles, never ownership UID. */
public final class YsmTrustedPlayer {
    private YsmTrustedPlayer() {}
    static boolean captureTransportCurrent(ServerPlayer player,YsmAssetCoordinator.Identity expected){
        return player.server.isSameThread()&&player.server.getPlayerList().getPlayer(player.getUUID())==player
            &&player.connection==expected.listener()&&player.connection!=null&&player.connection.isAcceptingMessages()
            &&player.getUUID().equals(expected.playerId());
    }
    public static YsmAssetCoordinator.Connection capture(ServerPlayer player){
        if(player==null||!player.server.isSameThread())throw new IllegalArgumentException("YSM identity requires server thread");
        Object listener=player.connection;String verified=Long.toString(TrustedAccounts.socialUid(player));
        return new YsmAssetCoordinator.Connection(){
            @Override public YsmAssetCoordinator.Identity current(){
                if(!player.server.isSameThread()||player.connection!=listener)throw new IllegalArgumentException("YSM connection changed");
                String current=Long.toString(TrustedAccounts.socialUid(player));if(!verified.equals(current))throw new IllegalArgumentException("YSM UID changed");
                return new YsmAssetCoordinator.Identity(current,player.getUUID(),listener);
            }
            @Override public boolean fallbackTargetCurrent(YsmAssetCoordinator.Identity expected){
                return player.server.isSameThread()&&player.server.getPlayerList().getPlayer(player.getUUID())==player
                    &&player.connection==listener&&listener!=null&&player.connection.isAcceptingMessages()
                    &&expected.listener()==listener&&expected.playerId().equals(player.getUUID());
            }
        };
    }
}
