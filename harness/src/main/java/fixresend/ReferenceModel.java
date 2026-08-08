package fixresend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reference model of FIX session-level recovery. Once the session has settled, for each
 * direction (sender to receiver):
 *
 * <ol>
 *   <li>every application message the sender submitted reaches the receiver's application
 *       exactly once ({@link Violation.Kind#LOST}, {@link Violation.Kind#DUPLICATED});</li>
 *   <li>in increasing MsgSeqNum order ({@link Violation.Kind#OUT_OF_ORDER});</li>
 *   <li>gaps are filled with SequenceReset-GapFill only over admin messages
 *       ({@link Violation.Kind#APP_GAP_FILLED});</li>
 *   <li>both sides end logged on with each side expecting the other's next sequence number
 *       ({@link Violation.Kind#NOT_RECOVERED}).</li>
 * </ol>
 */
public final class ReferenceModel {
    private ReferenceModel() {
    }

    public static List<Violation> check(RunTrace t) {
        List<Violation> out = new ArrayList<>();
        for (Side receiver : Side.values()) {
            Side sender = receiver.other();
            List<RunTrace.Submitted> sent = t.submitted.get(sender);
            List<RunTrace.Received> got = t.received.get(receiver);

            Map<String, Integer> counts = new HashMap<>();
            for (RunTrace.Received r : got) {
                counts.merge(r.clOrdId(), 1, Integer::sum);
            }
            for (RunTrace.Submitted s : sent) {
                int c = counts.getOrDefault(s.clOrdId(), 0);
                if (c == 0) {
                    out.add(new Violation(Violation.Kind.LOST, receiver, s.clOrdId() + " seq " + s.seqNum()));
                } else if (c > 1) {
                    out.add(new Violation(Violation.Kind.DUPLICATED, receiver, s.clOrdId() + " x" + c));
                }
            }
            for (int i = 1; i < got.size(); i++) {
                if (got.get(i).seqNum() <= got.get(i - 1).seqNum()) {
                    out.add(new Violation(Violation.Kind.OUT_OF_ORDER, receiver,
                            got.get(i - 1).seqNum() + " then " + got.get(i).seqNum()));
                }
            }
            for (RunTrace.GapFill g : t.gapFills.get(receiver)) {
                for (RunTrace.Submitted s : sent) {
                    if (s.seqNum() >= g.seqNum() && s.seqNum() < g.newSeqNo()) {
                        out.add(new Violation(Violation.Kind.APP_GAP_FILLED, receiver,
                                "gap fill " + g.seqNum() + "->" + g.newSeqNo() + " skips " + s.clOrdId()));
                    }
                }
            }
            RunTrace.FinalState r = t.finalState.get(receiver);
            RunTrace.FinalState s = t.finalState.get(sender);
            if (r == null || s == null || !r.loggedOn() || r.nextTargetSeqNum() != s.nextSenderSeqNum()) {
                out.add(new Violation(Violation.Kind.NOT_RECOVERED, receiver, r == null || s == null ? "no state"
                        : "loggedOn=" + r.loggedOn() + " expects " + r.nextTargetSeqNum()
                                + ", peer next " + s.nextSenderSeqNum()));
            }
        }
        return out;
    }

    public static boolean losesOrDuplicates(List<Violation> violations) {
        return violations.stream().anyMatch(v -> v.kind() == Violation.Kind.LOST
                || v.kind() == Violation.Kind.DUPLICATED);
    }
}
