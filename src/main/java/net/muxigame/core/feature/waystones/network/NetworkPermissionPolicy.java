package net.muxigame.core.feature.waystones.network;

import java.util.Objects;

/** Server-side policy. Facts must come from world/API adapters, never a browser or marker. */
public final class NetworkPermissionPolicy {
    private NetworkPermissionPolicy(){}
    public enum Origin {STONE, SERVER_FACILITY, PLAYER_PORTAL, NONE}
    public enum Result {
        ALLOWED, INVALID_PLAYER, NO_NEARBY_ENTRANCE, DISCONNECTED_PORTAL,
        PLAYER_PORTAL_NOT_AN_ENTRANCE, INVALID_TARGET, NOT_ACTIVATED,
        CROSS_DIMENSION_NETWORK_NOT_CONNECTED, ORIGINAL_PERMISSION_DENIED,
        STALE_CONNECTION, STALE_ENTRANCE, STALE_ASSOCIATION
    }
    public record Facts(boolean playerValid,Origin origin,boolean nearby,
                        boolean portalValid,boolean associationValid,
                        boolean targetValid,boolean activated,boolean sharestone,
                        boolean crossDimension,boolean currentDimensionHasServerGateway,
                        boolean originalRulesPass,boolean connectionCurrent,
                        boolean entranceCurrent,boolean associationCurrent) {
        public Facts {Objects.requireNonNull(origin);}
    }
    public static Result authorize(Facts f){
        if(!f.connectionCurrent())return Result.STALE_CONNECTION;
        if(!f.playerValid())return Result.INVALID_PLAYER;
        if(f.origin()==Origin.NONE || !f.nearby())return Result.NO_NEARBY_ENTRANCE;
        if(!f.entranceCurrent())return Result.STALE_ENTRANCE;
        if(f.origin()==Origin.PLAYER_PORTAL)return Result.PLAYER_PORTAL_NOT_AN_ENTRANCE;
        if(f.origin()==Origin.SERVER_FACILITY){
            if(!f.portalValid() || !f.associationValid())return Result.DISCONNECTED_PORTAL;
            if(!f.associationCurrent())return Result.STALE_ASSOCIATION;
        }
        if(!f.targetValid())return Result.INVALID_TARGET;
        if(!f.sharestone() && !f.activated())return Result.NOT_ACTIVATED;
        if(f.crossDimension() && !f.currentDimensionHasServerGateway())return Result.CROSS_DIMENSION_NETWORK_NOT_CONNECTED;
        if(!f.originalRulesPass())return Result.ORIGINAL_PERMISSION_DENIED;
        return Result.ALLOWED;
    }
    /** Player-built gate markers never assert network gateway authority. */
    public static boolean showPortalBadge(boolean actualPortalValid,boolean actualStoneValid,boolean actualAssociationValid){
        return actualPortalValid && actualStoneValid && actualAssociationValid;
    }
    public static boolean inCandidateRange(int dx,int dy,int dz,int radius,int maxVertical){
        if(radius<1 || radius>8 || maxVertical<0 || maxVertical>8)throw new IllegalArgumentException("Invalid configured range");
        if(Math.abs((long)dx)>radius || Math.abs((long)dy)>maxVertical || Math.abs((long)dz)>radius)return false;
        return (long)dx*dx+(long)dy*dy+(long)dz*dz<=(long)radius*radius;
    }
}
