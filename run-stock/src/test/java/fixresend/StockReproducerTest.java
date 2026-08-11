package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

/** Unmodified QuickFIX/J 2.3.2: the #1114 reproducer must fail, i.e. messages are lost. */
class StockReproducerTest {

    @Test
    void stockSessionIsOnClasspath() {
        assertFalse(Build.patchedSessionOnClasspath());
    }

    @Test
    void singleDisconnectAtResendRequestLosesTheGap() {
        Config c = new Config(2, false, StoreType.MEMORY);
        RunTrace t = Scenario.run(c, Schedule.parse("15:BEFORE"));
        assertEquals("ResendRequest", t.send(15).typeName());
        List<Violation> v = ReferenceModel.check(t);
        assertTrue(v.stream().anyMatch(x -> x.kind() == Violation.Kind.LOST), v.toString());
        assertTrue(v.stream().anyMatch(x -> x.kind() == Violation.Kind.NOT_RECOVERED), v.toString());
        // the engine never asks again on the new connection
        assertTrue(t.outbound.stream().noneMatch(o -> o.side() == Side.ACCEPTOR && o.msgType().equals("2")
                && o.connectionId() > t.send(15).connectionId()));
    }

    @Test
    void sameScheduleIsLostWithFileStore() {
        RunTrace t = Scenario.run(new Config(2, false, StoreType.FILE), Schedule.parse("15:BEFORE"));
        assertTrue(ReferenceModel.losesOrDuplicates(ReferenceModel.check(t)));
    }

    @Test
    void upstreamRegressionTestFailsOnStockBuild() {
        Result r = JUnitCore.runClasses(quickfix.SessionResendRequestNotSentTest.class);
        assertEquals(1, r.getRunCount());
        assertEquals(1, r.getFailureCount(), "expected the upstream test to fail against stock 2.3.2");
    }

    @Test
    void redundantResendWorkaroundRecoversTheSameSchedule() {
        RunTrace t = Scenario.run(new Config(2, true, StoreType.MEMORY), Schedule.parse("15:BEFORE"));
        assertEquals(List.of(), ReferenceModel.check(t));
    }
}
