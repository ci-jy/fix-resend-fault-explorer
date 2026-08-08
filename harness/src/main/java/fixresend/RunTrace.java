package fixresend;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Everything one scenario run observed; the reference model judges it. */
public final class RunTrace {

    /** An application message submitted by the script, with the MsgSeqNum the engine gave it. */
    public record Submitted(int seqNum, String clOrdId) {
    }

    /** An application message handed to {@code Application.fromApp}. */
    public record Received(int seqNum, String clOrdId, boolean possDup) {
    }

    /** A SequenceReset-GapFill accepted by the receiver: seqNum up to newSeqNo - 1 are skipped. */
    public record GapFill(int seqNum, int newSeqNo) {
    }

    /** Session state once the run has settled (or timed out). */
    public record FinalState(boolean loggedOn, int nextSenderSeqNum, int nextTargetSeqNum,
                             boolean resendRequested) {
    }

    public final Schedule schedule;
    public final List<OutboundRecord> outbound = new ArrayList<>();
    public final Map<Side, List<Submitted>> submitted = new EnumMap<>(Side.class);
    public final Map<Side, List<Received>> received = new EnumMap<>(Side.class);
    public final Map<Side, List<GapFill>> gapFills = new EnumMap<>(Side.class);
    public final Map<Side, FinalState> finalState = new EnumMap<>(Side.class);
    public final List<String> events = new ArrayList<>();
    public int connections;
    public long simulatedMillis;
    public boolean settled;

    public RunTrace(Schedule schedule) {
        this.schedule = schedule;
        for (Side s : Side.values()) {
            submitted.put(s, new ArrayList<>());
            received.put(s, new ArrayList<>());
            gapFills.put(s, new ArrayList<>());
        }
    }

    public int totalSends() {
        return outbound.size();
    }

    public OutboundRecord send(int sendIndex) {
        return sendIndex >= 1 && sendIndex <= outbound.size() ? outbound.get(sendIndex - 1) : null;
    }

    /** ResendRequests actually written to the wire by a side. */
    public int resendRequestsSent(Side side) {
        return (int) outbound.stream()
                .filter(o -> o.side() == side && o.transmitted() && o.msgType().equals("2")).count();
    }

    /**
     * ResendRequests written on a connection that ask again for a BeginSeqNo the same side
     * already requested on that connection (the flood SendRedundantResendRequests=Y risks).
     */
    public int duplicateResendRequests() {
        int dup = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (OutboundRecord o : outbound) {
            if (o.transmitted() && o.msgType().equals("2")) {
                if (!seen.add(o.side() + "/" + o.connectionId() + "/" + o.beginSeqNo())) {
                    dup++;
                }
            }
        }
        return dup;
    }

    /** Human-readable description of the faulted sends, e.g. "#14 A ResendRequest BEFORE". */
    public String describeFaults() {
        if (schedule.faults().isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Fault f : schedule.faults()) {
            OutboundRecord o = send(f.sendIndex());
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append('#').append(f.sendIndex()).append(' ');
            sb.append(o == null ? "?" : o.side().shortName() + " " + o.typeName() + "(" + o.seqNum() + ")");
            sb.append(' ').append(f.mode());
        }
        return sb.toString();
    }
}
