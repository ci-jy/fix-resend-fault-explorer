package fixresend;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ThreadLocalRandom;

/** Binds loopback listeners on a free port between 20000 and 29999. */
public final class Ports {
    public static final int MIN = 20000;
    public static final int MAX = 29999;

    private Ports() {
    }

    public static ServerSocketChannel bindInRange() throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            int port = ThreadLocalRandom.current().nextInt(MIN, MAX + 1);
            ServerSocketChannel ch = ServerSocketChannel.open();
            try {
                ch.bind(new InetSocketAddress("127.0.0.1", port));
                return ch;
            } catch (IOException e) {
                ch.close();
                last = e;
            }
        }
        throw new IOException("no free port in " + MIN + "-" + MAX, last);
    }
}
