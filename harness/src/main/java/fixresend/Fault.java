package fixresend;

/** Cut the connection at the {@code sendIndex}-th outbound send (1-based, counted across both sides). */
public record Fault(int sendIndex, CutMode mode) {
    @Override
    public String toString() {
        return sendIndex + ":" + mode;
    }

    public static Fault parse(String s) {
        String[] p = s.split(":");
        return new Fault(Integer.parseInt(p[0]), CutMode.valueOf(p[1]));
    }
}
