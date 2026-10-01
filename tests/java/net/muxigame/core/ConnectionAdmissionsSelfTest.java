package net.muxigame.core;

import net.muxigame.core.feature.login.ConnectionAdmissions;
import java.util.concurrent.atomic.AtomicLong;

final class ConnectionAdmissionsSelfTest {
    private static int passed;
    private static void check(String label,boolean value){if(!value)throw new AssertionError(label);passed++;}
    static int run(){
        passed=0;AtomicLong now=new AtomicLong();
        ConnectionAdmissions<Object> evidence=new ConnectionAdmissions<>(100,now::get);
        // Equal identifiers are deliberately insufficient; the transport object must be identical.
        Object a=new String("same-user"), b=new String("same-user");
        evidence.record(a);
        check("equal UID cannot borrow another connection's admission",!evidence.consume(b));
        check("the actual connection can consume its proof",evidence.consume(a));
        check("admission cannot be replayed",!evidence.consume(a));
        evidence.record(a);now.set(100);
        check("expired admission fails at the boundary",!evidence.consume(a));
        evidence.record(a);evidence.discard(a);
        check("disconnect removes pending proof",!evidence.consume(a));
        evidence.record(a);evidence.clear();
        check("shutdown removes proof",!evidence.consume(a));
        evidence.record(a);evidence.record(b);
        check("different connections retain separate proof",evidence.consume(a)&&evidence.consume(b));
        return passed;
    }
}
