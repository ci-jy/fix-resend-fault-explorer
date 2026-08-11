package fixresend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Real loopback TCP through the FIX proxy must reproduce the in-JVM outcome (stock build). */
class StockTcpConfirmTest {
    @Test
    void sampledSchedulesAgreeOverRealSockets() throws Exception {
        Config c = new Config(2, false, StoreType.MEMORY);
        List<Schedule> sample = TcpConfirm.sample(Build.STOCK, c, 30);
        boolean sawLoss = false;
        for (Schedule s : sample) {
            TcpConfirm.Comparison cmp = TcpConfirm.compare(c, s);
            assertTrue(cmp.agrees(), () -> s + " in-JVM " + cmp.inJvm() + " tcp " + cmp.tcp());
            sawLoss |= ReferenceModel.losesOrDuplicates(cmp.tcp());
        }
        assertTrue(sawLoss, "the #1114 loss must also show over TCP");
    }

    @Test
    void midFrameCutAtResendRequestLosesMessagesOverTcp() throws Exception {
        TcpConfirm.Comparison cmp = TcpConfirm.compare(new Config(2, false, StoreType.MEMORY),
                Schedule.parse("15:MID"));
        assertTrue(cmp.agrees());
        assertTrue(cmp.partialFrames() == 1);
        assertTrue(ReferenceModel.losesOrDuplicates(cmp.tcp()));
    }
}
