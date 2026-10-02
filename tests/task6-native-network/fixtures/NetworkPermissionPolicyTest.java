import net.muxigame.core.feature.waystones.network.NetworkPermissionPolicy;
import static net.muxigame.core.feature.waystones.network.NetworkPermissionPolicy.*;
public final class NetworkPermissionPolicyTest {
    static int checks;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static Facts facts(Origin origin,boolean nearby,boolean portal,boolean association,boolean target,boolean activated,boolean shared,boolean cross,boolean gateway,boolean rules,boolean connection,boolean entrance,boolean revision){return new Facts(true,origin,nearby,portal,association,target,activated,shared,cross,gateway,rules,connection,entrance,revision);}
    static Facts normal(Origin origin,boolean cross,boolean gateway){return facts(origin,true,true,true,true,true,false,cross,gateway,true,true,true,true);}
    public static void main(String[]args){
        check(authorize(normal(Origin.STONE,false,false))==Result.ALLOWED,"same dimension unchanged without gate");
        check(authorize(normal(Origin.STONE,true,false))==Result.CROSS_DIMENSION_NETWORK_NOT_CONNECTED,"no deployed gate denies cross dimension");
        check(authorize(normal(Origin.STONE,true,true))==Result.ALLOWED,"verified current dimension facility permits original legal transfer");
        check(authorize(normal(Origin.SERVER_FACILITY,true,true))==Result.ALLOWED,"actual nearby registered associated facility origin");
        check(authorize(normal(Origin.PLAYER_PORTAL,false,true))==Result.PLAYER_PORTAL_NOT_AN_ENTRANCE,"player gate badge alone adds no origin authority");
        check(authorize(normal(Origin.NONE,false,true))==Result.NO_NEARBY_ENTRANCE,"gate somewhere in dimension does not create nearby origin");
        check(authorize(facts(Origin.STONE,false,true,true,true,true,false,false,true,true,true,true,true))==Result.NO_NEARBY_ENTRANCE,"global gate cannot override distance");
        check(authorize(facts(Origin.SERVER_FACILITY,true,false,true,true,true,false,false,true,true,true,true,true))==Result.DISCONNECTED_PORTAL,"broken registered gate denied");
        check(authorize(facts(Origin.SERVER_FACILITY,true,true,false,true,true,false,false,true,true,true,true,true))==Result.DISCONNECTED_PORTAL,"unbound gate denied");
        check(authorize(facts(Origin.STONE,true,true,true,false,true,false,false,true,true,true,true,true))==Result.INVALID_TARGET,"arbitrary/nonstone target denied");
        check(authorize(facts(Origin.STONE,true,true,true,true,false,false,false,true,true,true,true,true))==Result.NOT_ACTIVATED,"visible GLOBAL ordinary target still needs activation");
        check(authorize(facts(Origin.STONE,true,true,true,true,false,true,false,false,true,true,true,true))==Result.ALLOWED,"Sharestone original exemption retained");
        check(authorize(facts(Origin.STONE,true,true,true,true,false,true,true,false,true,true,true,true))==Result.CROSS_DIMENSION_NETWORK_NOT_CONNECTED,"shared target cannot bypass dimension gate");
        check(authorize(facts(Origin.STONE,true,true,true,true,true,false,true,true,false,true,true,true))==Result.ORIGINAL_PERMISSION_DENIED,"original fees/events/permissions still veto");
        check(authorize(facts(Origin.STONE,true,true,true,true,true,false,true,true,true,false,true,true))==Result.STALE_CONNECTION,"reconnect invalidation");
        check(authorize(facts(Origin.STONE,true,true,true,true,true,false,true,true,true,true,false,true))==Result.STALE_ENTRANCE,"walk/entrance generation invalidation");
        check(authorize(facts(Origin.SERVER_FACILITY,true,true,true,true,true,false,true,true,true,true,true,false))==Result.STALE_ASSOCIATION,"association revision invalidation");
        check(showPortalBadge(true,true,true),"actual player portal can have display-only badge");
        check(!showPortalBadge(false,true,true)&&!showPortalBadge(true,false,true)&&!showPortalBadge(true,true,false),"no fabricated badge");
        check(inCandidateRange(4,0,0,4,3),"candidate horizontal boundary");
        check(!inCandidateRange(0,4,0,4,3),"candidate vertical cap differs from full sphere");
        check(inCandidateRange(1,3,2,4,3),"candidate diagonal inside");
        check(!inCandidateRange(3,3,0,4,3),"candidate diagonal outside");
        check(!inCandidateRange(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,4,3),"untrusted integer overflow cannot grant range");
        check(!inCandidateRange(Integer.MAX_VALUE,0,Integer.MAX_VALUE,4,3),"large range values denied");
        boolean invalid=false;try{inCandidateRange(0,0,0,100,3);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"bounded configurable scan");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"scope\":\"isolated policy with synthetic server facts; actual world adapters covered separately by production runtime tests\"}");
    }
}
