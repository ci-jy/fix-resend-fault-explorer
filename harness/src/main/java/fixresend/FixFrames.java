package fixresend;

/** Minimal tag lookups and framing for raw FIX strings (SOH-delimited). */
public final class FixFrames {
    public static final char SOH = '\u0001';

    private FixFrames() {
    }

    /** Value of the first occurrence of {@code tag}, or null. */
    public static String field(String frame, int tag) {
        String key = tag + "=";
        int i = frame.startsWith(key) ? 0 : -1;
        if (i < 0) {
            int j = frame.indexOf(SOH + key);
            if (j < 0) {
                return null;
            }
            i = j + 1;
        }
        int start = i + key.length();
        int end = frame.indexOf(SOH, start);
        return frame.substring(start, end < 0 ? frame.length() : end);
    }

    public static int intField(String frame, int tag) {
        String v = field(frame, tag);
        return v == null ? 0 : Integer.parseInt(v);
    }

    /**
     * Length of the complete frame at the start of {@code buf[0..len)}, or -1 if more bytes are
     * needed. Uses BodyLength(9): frame = "8=..|9=N|" + N body bytes + "10=xxx|".
     */
    public static int frameLength(byte[] buf, int len) {
        int firstSoh = indexOf(buf, len, 0);
        if (firstSoh < 0) {
            return -1;
        }
        int lenStart = firstSoh + 1;
        if (lenStart + 2 > len) {
            return -1;
        }
        if (buf[lenStart] != '9' || buf[lenStart + 1] != '=') {
            throw new IllegalArgumentException("BodyLength(9) must be the second field");
        }
        int secondSoh = indexOf(buf, len, lenStart);
        if (secondSoh < 0) {
            return -1;
        }
        int bodyLength = Integer.parseInt(new String(buf, lenStart + 2, secondSoh - lenStart - 2,
                java.nio.charset.StandardCharsets.US_ASCII));
        int total = secondSoh + 1 + bodyLength + 7;
        return total <= len ? total : -1;
    }

    private static int indexOf(byte[] buf, int len, int from) {
        for (int i = from; i < len; i++) {
            if (buf[i] == SOH) {
                return i;
            }
        }
        return -1;
    }
}
