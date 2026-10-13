package net.muxigame.core.feature.ysmassets;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Machine authority boundaries; fixed fixture credentials, no production provider. */
public final class YsmMachineAuthorityTest {
    static int checks;
    static final long NOW=1_800_000_000_000L;
    static final byte[] SECRET="fixed-machine-boundary-fixture-not-production".getBytes(StandardCharsets.UTF_8);
    static final byte[] BODY="{}".getBytes(StandardCharsets.UTF_8);
    static final String PATH="/internal/ysm/connection/resolve",PURPOSE="connection-resolve",AUDIENCE="ysm-core:fixture";
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void denied(Runnable task,String message){boolean rejected=false;try{task.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,message);}
    static YsmMachineAuthority.Credential key(String id){return new YsmMachineAuthority.Credential("fixture-web",id,SECRET,Set.of(PURPOSE),Set.of(AUDIENCE));}
    static YsmMachineAuthority.Proof sign(YsmMachineAuthority.Credential key,long timestamp,String nonce){return YsmMachineAuthority.sign(key,PURPOSE,AUDIENCE,"POST",PATH,BODY,timestamp,nonce);}
    public static void main(String[] args)throws Exception {
        var key=key("registered-key");var authority=new YsmMachineAuthority(AUDIENCE,id->key,new YsmMachineAuthority.MemoryReplayStore(),()->NOW);
        var valid=sign(key,NOW,"1".repeat(32));check(authority.verify(valid,PURPOSE,"POST",PATH,BODY).keyId().equals(key.keyId()),"registered key accepted");
        denied(()->authority.verify(valid,PURPOSE,"POST",PATH,BODY),"replay denied");
        var alias=sign(key("unregistered-alias"),NOW,"2".repeat(32));denied(()->authority.verify(alias,PURPOSE,"POST",PATH,BODY),"provider returning other registered key cannot bless alias");
        check(authority.verify(sign(key,NOW,"2".repeat(32)),PURPOSE,"POST",PATH,BODY)!=null,"denied alias cannot consume valid nonce");
        denied(()->authority.verify(sign(key,NOW,"3".repeat(32)),PURPOSE,"POST",PATH,"changed".getBytes(StandardCharsets.UTF_8)),"body tamper denied");
        denied(()->authority.verify(sign(key,NOW,"4".repeat(32)),"player-read","POST",PATH,BODY),"wrong purpose denied");
        denied(()->authority.verify(sign(key,NOW-30_001,"5".repeat(32)),PURPOSE,"POST",PATH,BODY),"expired proof denied");
        denied(()->authority.verify(sign(key,NOW+30_001,"6".repeat(32)),PURPOSE,"POST",PATH,BODY),"future proof denied");
        check(authority.verify(sign(key,NOW-30_000,"7".repeat(32)),PURPOSE,"POST",PATH,BODY)!=null,"lower time boundary inclusive");
        check(authority.verify(sign(key,NOW+30_000,"8".repeat(32)),PURPOSE,"POST",PATH,BODY)!=null,"upper time boundary inclusive");
        var mismatch=new YsmMachineAuthority("ysm-core:other",id->key,new YsmMachineAuthority.MemoryReplayStore(),()->NOW);
        denied(()->mismatch.verify(sign(key,NOW,"9".repeat(32)),PURPOSE,"POST",PATH,BODY),"wrong audience denied");
        boolean disabled=false;try{new YsmMachineAuthority(AUDIENCE,null,null,()->NOW).verify(valid,PURPOSE,"POST",PATH,BODY);}catch(YsmMachineAuthority.Unconfigured expected){disabled=true;}check(disabled,"missing provider disabled");
        check(!key.toString().contains(new String(SECRET,StandardCharsets.UTF_8)),"credential representation redacts secret");
        var concurrent=sign(key,NOW,"a".repeat(32));var pool=Executors.newFixedThreadPool(8);
        try{List<Callable<Boolean>> calls=new ArrayList<>();for(int i=0;i<32;i++)calls.add(()->{try{authority.verify(concurrent,PURPOSE,"POST",PATH,BODY);return true;}catch(IllegalArgumentException rejected){return false;}});int accepted=0;for(var result:pool.invokeAll(calls))if(result.get())accepted++;check(accepted==1,"concurrent nonce accepted exactly once");}finally{pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"owned test threads stopped");}
        System.out.println("{\"passed\":true,\"checks\":"+checks+"}");
    }
}
