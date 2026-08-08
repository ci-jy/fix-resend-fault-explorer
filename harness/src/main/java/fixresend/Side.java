package fixresend;

/** The two ends of the simulated FIX session. */
public enum Side {
    INITIATOR, ACCEPTOR;

    public Side other() {
        return this == INITIATOR ? ACCEPTOR : INITIATOR;
    }

    public String shortName() {
        return this == INITIATOR ? "I" : "A";
    }
}
