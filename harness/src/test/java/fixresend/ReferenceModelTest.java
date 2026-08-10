package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceModelTest {

    private static RunTrace cleanTrace() {
        RunTrace t = new RunTrace(Schedule.NONE);
        t.submitted.get(Side.INITIATOR).addAll(List.of(new RunTrace.Submitted(2, "I-1"), new RunTrace.Submitted(4, "I-2")));
        t.submitted.get(Side.ACCEPTOR).add(new RunTrace.Submitted(2, "A-1"));
        t.received.get(Side.ACCEPTOR).addAll(List.of(new RunTrace.Received(2, "I-1", false), new RunTrace.Received(4, "I-2", true)));
        t.received.get(Side.INITIATOR).add(new RunTrace.Received(2, "A-1", false));
        t.finalState.put(Side.INITIATOR, new RunTrace.FinalState(true, 5, 3, false));
        t.finalState.put(Side.ACCEPTOR, new RunTrace.FinalState(true, 3, 5, false));
        return t;
    }

    private static List<Violation.Kind> kinds(RunTrace t) {
        return ReferenceModel.check(t).stream().map(Violation::kind).toList();
    }

    @Test
    void cleanRecoveryHasNoViolations() {
        assertEquals(List.of(), ReferenceModel.check(cleanTrace()));
    }

    @Test
    void missingApplicationMessageIsLost() {
        RunTrace t = cleanTrace();
        t.received.get(Side.ACCEPTOR).remove(1);
        assertEquals(List.of(Violation.Kind.LOST), kinds(t));
        assertTrue(ReferenceModel.losesOrDuplicates(ReferenceModel.check(t)));
    }

    @Test
    void secondDeliveryIsDuplicated() {
        RunTrace t = cleanTrace();
        t.received.get(Side.INITIATOR).add(new RunTrace.Received(2, "A-1", true));
        assertTrue(kinds(t).contains(Violation.Kind.DUPLICATED));
    }

    @Test
    void deliveryOrderMustFollowSequenceNumbers() {
        RunTrace t = cleanTrace();
        List<RunTrace.Received> r = t.received.get(Side.ACCEPTOR);
        r.add(0, r.remove(1));
        assertEquals(List.of(Violation.Kind.OUT_OF_ORDER), kinds(t));
    }

    @Test
    void gapFillOverApplicationMessageIsReported() {
        RunTrace t = cleanTrace();
        t.gapFills.get(Side.ACCEPTOR).add(new RunTrace.GapFill(3, 5));
        assertEquals(List.of(Violation.Kind.APP_GAP_FILLED), kinds(t));
    }

    @Test
    void gapFillOverAdminMessagesIsAllowed() {
        RunTrace t = cleanTrace();
        t.gapFills.get(Side.ACCEPTOR).add(new RunTrace.GapFill(3, 4));
        assertEquals(List.of(), kinds(t));
    }

    @Test
    void sessionStuckBehindPeerIsNotRecovered() {
        RunTrace t = cleanTrace();
        t.finalState.put(Side.ACCEPTOR, new RunTrace.FinalState(true, 3, 4, true));
        assertEquals(List.of(Violation.Kind.NOT_RECOVERED), kinds(t));
    }
}
