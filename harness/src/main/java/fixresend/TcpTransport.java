package fixresend;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Carries every frame over real loopback TCP: initiator socket, {@link FixProxy}, acceptor
 * socket. The proxy executes the cut; the bytes the peer gets are read back from its socket.
 */
public final class TcpTransport implements Transport {
    private final ServerSocketChannel acceptorListener;
    private final FixProxy proxy;
    private Socket initiatorSocket;
    private Socket acceptorSocket;
    private int partialFrames;
    private int framesRelayed;

    public TcpTransport() throws IOException {
        acceptorListener = Ports.bindInRange();
        proxy = new FixProxy((InetSocketAddress) acceptorListener.getLocalAddress());
    }

    @Override
    public void connect(int connectionId) throws IOException {
        disconnect();
        initiatorSocket = new Socket();
        initiatorSocket.connect(proxy.address(), 2000);
        initiatorSocket.setTcpNoDelay(true);
        acceptorSocket = acceptorListener.socket().accept();
        acceptorSocket.setTcpNoDelay(true);
    }

    /** Frames the far end received only partially (MID cuts) and discarded. */
    public int partialFrames() {
        return partialFrames;
    }

    public int framesRelayed() {
        return framesRelayed;
    }

    @Override
    public String transmit(Side from, String frame, CutMode cut) throws Exception {
        Socket src = from == Side.INITIATOR ? initiatorSocket : acceptorSocket;
        Socket dst = from == Side.INITIATOR ? acceptorSocket : initiatorSocket;
        proxy.cutNextFrame(cut);
        OutputStream out = src.getOutputStream();
        out.write(frame.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        FixProxy.Relayed r = proxy.awaitRelayed();
        framesRelayed++;
        if (r.bytesForwarded() == 0) {
            return null;
        }
        byte[] got = readFrame(dst.getInputStream(), r.bytesForwarded());
        if (got == null) {
            partialFrames++;
            return null;
        }
        return new String(got, StandardCharsets.ISO_8859_1);
    }

    /** Reads {@code expected} bytes and returns them if they form one complete FIX frame. */
    private static byte[] readFrame(InputStream in, int expected) throws IOException {
        byte[] buf = new byte[expected];
        int len = 0;
        try {
            while (len < expected) {
                int n = in.read(buf, len, expected - len);
                if (n < 0) {
                    break;
                }
                len += n;
            }
        } catch (SocketTimeoutException e) {
            // fall through with what arrived
        }
        int f = FixFrames.frameLength(buf, len);
        return f < 0 ? null : Arrays.copyOf(buf, f);
    }

    @Override
    public void disconnect() {
        for (Socket s : new Socket[] {initiatorSocket, acceptorSocket}) {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // closing anyway
                }
            }
        }
        initiatorSocket = null;
        acceptorSocket = null;
    }

    @Override
    public void close() {
        disconnect();
        proxy.close();
        try {
            acceptorListener.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
