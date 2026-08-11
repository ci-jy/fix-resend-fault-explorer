package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Real loopback TCP through the FIX proxy must reproduce the in-JVM outcome (patched build). */
class PatchedTcpConfirmTest {
    @Test
    void sampledSchedulesAgreeOverRealSockets() throws Exception {
        Config c = new Config(2, false, StoreType.FILE);
        for (Schedule s : TcpConfirm.sample(Build.PATCHED, c, 30)) {
            TcpConfirm.Comparison cmp = TcpConfirm.compare(c, s);
            assertTrue(cmp.agrees(), () -> s + " in-JVM " + cmp.inJvm() + " tcp " + cmp.tcp());
            assertTrue(!ReferenceModel.losesOrDuplicates(cmp.tcp()));
        }
    }

    @Test
    void midFrameCutAtResendRequestRecoversOverTcp() throws Exception {
        TcpConfirm.Comparison cmp = TcpConfirm.compare(new Config(2, false, StoreType.MEMORY),
                Schedule.parse("15:MID"));
        assertTrue(cmp.agrees());
        assertEquals(List.of(), cmp.tcp());
    }
}
