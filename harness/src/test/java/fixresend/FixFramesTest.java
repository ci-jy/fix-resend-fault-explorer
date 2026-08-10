package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FixFramesTest {
    static final String HB = frame("35=0\u000134=7\u000149=A\u000156=B\u0001");

    static String frame(String body) {
        String head = "8=FIX.4.4\u00019=" + body.length() + "\u0001";
        int sum = 0;
        for (byte b : (head + body).getBytes(StandardCharsets.ISO_8859_1)) {
            sum += b;
        }
        return head + body + String.format("10=%03d\u0001", sum % 256);
    }

    @Test
    void looksUpTags() {
        assertEquals("0", FixFrames.field(HB, 35));
        assertEquals(7, FixFrames.intField(HB, 34));
        assertEquals("FIX.4.4", FixFrames.field(HB, 8));
        assertNull(FixFrames.field(HB, 43));
    }

    @Test
    void findsCompleteFrameLength() {
        byte[] b = (HB + HB).getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(HB.length(), FixFrames.frameLength(b, b.length));
    }

    @Test
    void partialFrameNeedsMoreBytes() {
        byte[] b = HB.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(-1, FixFrames.frameLength(b, b.length - 1));
        assertEquals(-1, FixFrames.frameLength(b, b.length / 2));
        assertEquals(-1, FixFrames.frameLength(b, 5));
    }
}
