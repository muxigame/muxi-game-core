package net.muxigame.core.feature.login;
import net.muxigame.core.feature.identity.IdentityRules;
import java.util.UUID;
/** Checks a game profile claim only. A matching profile is NOT account authentication. */
final class TerminalSsoIdentity {
    private TerminalSsoIdentity(){}
    static boolean consistent(String uid,UUID uuid){return uid!=null && IdentityRules.validUid(uid) && uuid!=null && uuid.equals(IdentityRules.offlineUuid(uid));}
}
