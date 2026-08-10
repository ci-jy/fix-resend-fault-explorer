package fixresend;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A loopback TCP proxy that understands FIX framing. It relays complete frames between the
 * initiator's connection and the acceptor, and on instruction cuts the connection before,
 * during (half the frame's bytes) or after the next frame. Runs on one NIO selector thread.
 */
public final class FixProxy implements AutoCloseable {

    /** What happened to one relayed frame. */
    public record Relayed(int frameNumber, CutMode cut, int bytesForwarded, int frameLength) {
    }

    private final ServerSocketChannel listener;
    private final InetSocketAddress upstream;
    private final Selector selector;
    private final Thread thread;
    private final BlockingQueue<Relayed> relayed = new LinkedBlockingQueue<>();
    private volatile CutMode nextCut;
    private volatile boolean running = true;
    private int frames;
    private Pair pair;

    private static final class Pair {
        final SocketChannel downstream;
        final SocketChannel upstream;
        final Framer fromDown = new Framer();
        final Framer fromUp = new Framer();

        Pair(SocketChannel downstream, SocketChannel upstream) {
            this.downstream = downstream;
            this.upstream = upstream;
        }
    }

    private static final class Framer {
        byte[] buf = new byte[8192];
        int len;

        void append(ByteBuffer b) {
            int n = b.remaining();
            if (len + n > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + n));
            }
            b.get(buf, len, n);
            len += n;
        }

        byte[] poll() {
            int f = FixFrames.frameLength(buf, len);
            if (f < 0) {
                return null;
            }
            byte[] frame = Arrays.copyOf(buf, f);
            System.arraycopy(buf, f, buf, 0, len - f);
            len -= f;
            return frame;
        }
    }

    /** Starts a proxy on a free port in 20000-29999 that forwards to {@code upstream}. */
    public FixProxy(InetSocketAddress upstream) throws IOException {
        this.upstream = upstream;
        this.listener = Ports.bindInRange();
        this.listener.configureBlocking(false);
        this.selector = Selector.open();
        listener.register(selector, SelectionKey.OP_ACCEPT);
        thread = new Thread(this::loop, "fix-proxy");
        thread.setDaemon(true);
        thread.start();
    }

    public InetSocketAddress address() throws IOException {
        return (InetSocketAddress) listener.getLocalAddress();
    }

    /** The cut applied to the next complete frame, in either direction (null = relay normally). */
    public void cutNextFrame(CutMode cut) {
        nextCut = cut;
    }

    /** Waits for the proxy to finish handling the next frame. */
    public Relayed awaitRelayed() throws InterruptedException {
        Relayed r = relayed.poll(5, java.util.concurrent.TimeUnit.SECONDS);
        if (r == null) {
            throw new IllegalStateException("proxy did not relay a frame within 5 s");
        }
        return r;
    }

    private void loop() {
        ByteBuffer buf = ByteBuffer.allocate(16384);
        try {
            while (running) {
                selector.select(100);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        SocketChannel down = listener.accept();
                        if (down == null) {
                            continue;
                        }
                        closePair();
                        SocketChannel up = SocketChannel.open(upstream);
                        down.configureBlocking(false);
                        up.configureBlocking(false);
                        pair = new Pair(down, up);
                        down.register(selector, SelectionKey.OP_READ, pair);
                        up.register(selector, SelectionKey.OP_READ, pair);
                    } else if (key.isReadable()) {
                        Pair p = (Pair) key.attachment();
                        SocketChannel ch = (SocketChannel) key.channel();
                        buf.clear();
                        int n;
                        try {
                            n = ch.read(buf);
                        } catch (IOException e) {
                            n = -1;
                        }
                        if (n < 0) {
                            if (p == pair) {
                                closePair();
                            } else {
                                key.cancel();
                            }
                            continue;
                        }
                        buf.flip();
                        boolean fromDown = ch == p.downstream;
                        Framer framer = fromDown ? p.fromDown : p.fromUp;
                        SocketChannel target = fromDown ? p.upstream : p.downstream;
                        framer.append(buf);
                        byte[] frame;
                        while (p == pair && (frame = framer.poll()) != null) {
                            relay(frame, target);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void relay(byte[] frame, SocketChannel target) throws IOException {
        frames++;
        CutMode cut = nextCut;
        nextCut = null;
        int forward = cut == null || cut == CutMode.AFTER ? frame.length : cut == CutMode.MID ? frame.length / 2 : 0;
        ByteBuffer out = ByteBuffer.wrap(frame, 0, forward);
        while (out.hasRemaining()) {
            target.write(out);
        }
        if (cut != null) {
            closePair();
        }
        relayed.add(new Relayed(frames, cut, forward, frame.length));
    }

    private void closePair() {
        if (pair != null) {
            try {
                pair.downstream.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            try {
                pair.upstream.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            pair = null;
        }
    }

    @Override
    public void close() {
        running = false;
        selector.wakeup();
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closePair();
        try {
            selector.close();
            listener.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
