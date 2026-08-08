package fixresend;

/** Direct hand-off between the sessions; the cut mode alone decides delivery. */
public final class InJvmTransport implements Transport {
    @Override
    public void connect(int connectionId) {
    }

    @Override
    public String transmit(Side from, String frame, CutMode cut) {
        return cut == null || cut.delivers() ? frame : null;
    }

    @Override
    public void disconnect() {
    }
}
