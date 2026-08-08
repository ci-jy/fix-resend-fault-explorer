package fixresend;

/** One breach of FIX gap-fill semantics found by the {@link ReferenceModel}. */
public record Violation(Kind kind, Side receiver, String detail) {

    public enum Kind {
        /** An application message the peer sent never reached the receiver's application. */
        LOST,
        /** An application message reached the receiver's application more than once. */
        DUPLICATED,
        /** Application messages reached the receiver out of MsgSeqNum order. */
        OUT_OF_ORDER,
        /** A SequenceReset-GapFill skipped over a sequence number that carried an application message. */
        APP_GAP_FILLED,
        /** The session never came back in sync (logged on with matching sequence numbers). */
        NOT_RECOVERED
    }

    @Override
    public String toString() {
        return kind + "@" + receiver.shortName() + "(" + detail + ")";
    }
}
