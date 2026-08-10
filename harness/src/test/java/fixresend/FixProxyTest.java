package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixProxyTest {
    @Test
    void relaysFramesOverLoopbackAndCutsMidFrame() throws Exception {
        try (TcpTransport t = new TcpTransport()) {
            t.connect(1);
            assertEquals(FixFramesTest.HB, t.transmit(Side.INITIATOR, FixFramesTest.HB, null));
            assertEquals(FixFramesTest.HB, t.transmit(Side.ACCEPTOR, FixFramesTest.HB, null));
            assertNull(t.transmit(Side.INITIATOR, FixFramesTest.HB, CutMode.MID));
            assertEquals(1, t.partialFrames());
            t.connect(2);
            assertEquals(FixFramesTest.HB, t.transmit(Side.ACCEPTOR, FixFramesTest.HB, CutMode.AFTER));
            t.connect(3);
            assertNull(t.transmit(Side.ACCEPTOR, FixFramesTest.HB, CutMode.BEFORE));
        }
    }

    @Test
    void bindsInsideTheAllowedPortRange() throws Exception {
        try (var ch = Ports.bindInRange()) {
            int port = ((java.net.InetSocketAddress) ch.getLocalAddress()).getPort();
            assertTrue(port >= 20000 && port <= 29999);
        }
    }
}
