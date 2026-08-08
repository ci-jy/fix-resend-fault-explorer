package fixresend;

/**
 * Where the connection is cut relative to the faulted outbound message.
 *
 * <ul>
 *   <li>{@link #BEFORE}: the drop is processed before the write, so {@code Responder.send}
 *       returns false and the message never reaches the peer (the #1114 race).</li>
 *   <li>{@link #MID}: real-TCP only; half of the frame reaches the peer, then the connection
 *       closes. The peer discards the partial frame, so it is equivalent to BEFORE.</li>
 *   <li>{@link #AFTER}: the message is written and delivered, then the connection drops.</li>
 * </ul>
 */
public enum CutMode {
    BEFORE, MID, AFTER;

    /** The in-JVM mode whose outcome a real-TCP cut must reproduce. */
    public CutMode inJvmEquivalent() {
        return this == MID ? BEFORE : this;
    }

    public boolean delivers() {
        return this == AFTER;
    }
}
