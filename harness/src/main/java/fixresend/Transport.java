package fixresend;

/** Carries raw FIX frames between the two sessions of the current connection. */
public interface Transport extends AutoCloseable {

    /** Establishes the link for a new connection. */
    void connect(int connectionId) throws Exception;

    /**
     * Writes one outbound frame.
     *
     * @param cut null for a normal send, otherwise how the connection is cut around this frame
     * @return the frame as received by the peer, or null if it never arrived
     */
    String transmit(Side from, String frame, CutMode cut) throws Exception;

    /** Tears down the current link (idempotent). */
    void disconnect();

    @Override
    default void close() {
        disconnect();
    }
}
