package fixresend;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.DefaultSessionFactory;
import quickfix.FileStoreFactory;
import quickfix.Log;
import quickfix.LogFactory;
import quickfix.MemoryStoreFactory;
import quickfix.Message;
import quickfix.MessageStoreFactory;
import quickfix.MessageUtils;
import quickfix.Responder;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionSettings;

/**
 * Runs one scripted FIX.4.4 conversation between an initiator and an acceptor session in this
 * JVM, injecting the disconnects of a {@link Schedule}, and records a {@link RunTrace}.
 *
 * <p>Everything is single-threaded and driven by a {@link SimClock}: the same config and
 * schedule always produce the same trace, so a schedule is a reproducible test case.
 */
public final class Scenario implements AutoCloseable {

    public static final SessionID INITIATOR_ID = new SessionID("FIX.4.4", "CLIENT", "BROKER");
    public static final SessionID ACCEPTOR_ID = new SessionID("FIX.4.4", "BROKER", "CLIENT");
    public static final int HEARTBEAT_SECONDS = 30;
    public static final int RECONNECT_SECONDS = 5;
    /** Simulated time allowed for recovery after the script ends. */
    public static final int SETTLE_SECONDS = 240;
    private static final int MAX_EVENTS_PER_PUMP = 100_000;

    private sealed interface Event permits Delivery, Eof {
    }

    /** A frame that reached the peer's socket buffer. */
    private record Delivery(Side to, int connectionId, String frame) implements Event {
    }

    /** End of stream observed by {@code to}; the session disconnects when it handles it. */
    private record Eof(Side to, int connectionId, String reason) implements Event {
    }

    private final Config config;
    private final Schedule schedule;
    private final Transport transport;
    private final SimClock clock = new SimClock();
    private final RunTrace trace;
    private final Deque<Event> events = new ArrayDeque<>();
    private final Deque<Event> held = new ArrayDeque<>();
    private Side stalled;
    private final Map<Side, Session> sessions = new EnumMap<>(Side.class);
    private final Map<Side, Shim> shims = new EnumMap<>(Side.class);
    private final boolean captureLog;
    private Path storeDir;
    private int connectionId;
    private boolean linkUp;
    private int sendCounter;
    private long downSince = -1;

    public Scenario(Config config, Schedule schedule, Transport transport, boolean captureLog) {
        this.config = config;
        this.schedule = schedule;
        this.transport = transport;
        this.captureLog = captureLog;
        this.trace = new RunTrace(schedule);
    }

    public static RunTrace run(Config config, Schedule schedule) {
        try (Scenario s = new Scenario(config, schedule, new InJvmTransport(), false)) {
            return s.execute();
        }
    }

    public Session session(Side side) {
        return sessions.get(side);
    }

    public SimClock clock() {
        return clock;
    }

    /** Runs the full script followed by the settle phase. */
    public RunTrace execute() {
        try {
            clock.install();
            createSessions();
            connect();
            runFor(2);
            int firstHalf = (config.appMessages() + 1) / 2;
            for (int i = 1; i <= firstHalf; i++) {
                submitPair(i);
            }
            // both sides go quiet long enough to exchange Heartbeats
            runFor(HEARTBEAT_SECONDS + 1);
            // the initiator stalls (no timers, inbound frames wait in its socket buffer), so the
            // acceptor hears nothing for 1.5 x HeartBtInt and sends a TestRequest
            stalled = Side.INITIATOR;
            for (int t = 0; t < HEARTBEAT_SECONDS * 8 / 5; t++) {
                clock.advance(1000);
                session(Side.ACCEPTOR).next();
                pump();
            }
            stalled = null;
            while (!held.isEmpty()) {
                events.addFirst(held.pollLast());
            }
            pump();
            // planned Logout and re-Logon in the middle of the stream
            session(Side.INITIATOR).logout("planned");
            runFor(3);
            session(Side.INITIATOR).logon();
            runFor(RECONNECT_SECONDS + 3);
            for (int i = firstHalf + 1; i <= config.appMessages(); i++) {
                submitPair(i);
            }
            runFor(2);
            for (int t = 0; t < SETTLE_SECONDS && !settled(); t++) {
                tick();
            }
            finish();
            return trace;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void submitPair(int i) throws IOException {
        submit(Side.INITIATOR, i);
        submit(Side.ACCEPTOR, i);
        pump();
        tick();
    }

    private void submit(Side side, int i) {
        String id = side.shortName() + "-" + i;
        Message m = new Message();
        m.getHeader().setString(35, "D");
        m.setString(11, id);
        m.setString(55, "SYM" + i);
        m.setChar(54, side == Side.INITIATOR ? '1' : '2');
        m.setInt(38, 100 * i);
        m.setChar(40, '1');
        m.setUtcTimeStamp(60, quickfix.SystemTime.getLocalDateTime());
        session(side).send(m);
        try {
            trace.submitted.get(side).add(new RunTrace.Submitted(m.getHeader().getInt(34), id));
        } catch (quickfix.FieldNotFound e) {
            throw new IllegalStateException(e);
        }
    }

    private void runFor(int seconds) throws IOException {
        for (int t = 0; t < seconds; t++) {
            tick();
        }
    }

    /** One simulated second: timers of both sessions, deliveries, and reconnects. */
    private void tick() throws IOException {
        clock.advance(1000);
        session(Side.INITIATOR).next();
        pump();
        session(Side.ACCEPTOR).next();
        pump();
        maybeReconnect();
    }

    private void maybeReconnect() {
        boolean down = !session(Side.INITIATOR).hasResponder() && !session(Side.ACCEPTOR).hasResponder()
                && events.isEmpty();
        if (!down) {
            downSince = -1;
            return;
        }
        if (!session(Side.INITIATOR).isEnabled()) {
            return;
        }
        if (downSince < 0) {
            downSince = clock.getTime();
        }
        if (clock.getTime() - downSince >= RECONNECT_SECONDS * 1000L) {
            connect();
            downSince = -1;
        }
    }

    private boolean settled() {
        Session i = session(Side.INITIATOR);
        Session a = session(Side.ACCEPTOR);
        return events.isEmpty() && i.isLoggedOn() && a.isLoggedOn()
                && i.getExpectedTargetNum() == a.getExpectedSenderNum()
                && a.getExpectedTargetNum() == i.getExpectedSenderNum();
    }

    private void connect() {
        connectionId++;
        trace.connections++;
        try {
            transport.connect(connectionId);
        } catch (Exception e) {
            throw new IllegalStateException("transport connect failed", e);
        }
        linkUp = true;
        // the initiator owns its socket at once; the acceptor binds it on the first Logon,
        // as quickfix.mina.acceptor.AcceptorIoHandler does
        for (Side side : Side.values()) {
            shims.put(side, new Shim(side, connectionId));
        }
        session(Side.INITIATOR).setResponder(shims.get(Side.INITIATOR));
        log(null, "connected #" + connectionId);
    }

    /** Delivers queued frames and end-of-stream notifications until the network is idle. */
    public void pump() {
        int n = 0;
        while (!events.isEmpty()) {
            if (++n > MAX_EVENTS_PER_PUMP) {
                throw new IllegalStateException("event storm; schedule " + schedule);
            }
            Event e = events.pollFirst();
            if (stalled != null && (e instanceof Delivery d0 && d0.to() == stalled
                    || e instanceof Eof e0 && e0.to() == stalled)) {
                held.addLast(e);
                continue;
            }
            if (e instanceof Delivery d) {
                Session s = session(d.to());
                if (d.to() == Side.ACCEPTOR && !s.hasResponder() && shims.get(Side.ACCEPTOR) != null
                        && shims.get(Side.ACCEPTOR).connectionId == d.connectionId() && linkUp
                        && d.connectionId() == connectionId) {
                    if (!"A".equals(FixFrames.field(d.frame(), 35))) {
                        log(d.to(), "ignoring non-logon message before session establishment");
                        continue;
                    }
                    s.setHeartBeatInterval(FixFrames.intField(d.frame(), 108));
                    s.setResponder(shims.get(Side.ACCEPTOR));
                }
                if (!isCurrent(d.to(), d.connectionId())) {
                    continue;
                }
                try {
                    s.next(MessageUtils.parse(s, d.frame()));
                } catch (Exception ex) {
                    log(d.to(), "exception from next(): " + ex);
                }
            } else if (e instanceof Eof eof) {
                if (isCurrent(eof.to(), eof.connectionId())) {
                    try {
                        session(eof.to()).disconnect(eof.reason(), false);
                    } catch (IOException ex) {
                        log(eof.to(), "exception from disconnect(): " + ex);
                    }
                }
            }
        }
    }

    private boolean isCurrent(Side side, int conn) {
        Shim shim = shims.get(side);
        return session(side).hasResponder() && shim != null && shim.connectionId == conn;
    }

    private void createSessions() throws IOException {
        SessionSettings settings = new SessionSettings();
        settings.setString("NonStopSession", "Y");
        settings.setString("HeartBtInt", Integer.toString(HEARTBEAT_SECONDS));
        settings.setString("ReconnectInterval", Integer.toString(RECONNECT_SECONDS));
        settings.setString("UseDataDictionary", "N");
        settings.setString("CheckLatency", "N");
        settings.setString("LogonTimeout", "10");
        settings.setString("LogoutTimeout", "2");
        settings.setString("SocketConnectHost", "127.0.0.1");
        settings.setString("SocketConnectPort", "20001");
        settings.setString("SocketAcceptPort", "20001");
        settings.setString("SendRedundantResendRequests", config.redundantResends() ? "Y" : "N");
        MessageStoreFactory storeFactory;
        if (config.store() == StoreType.FILE) {
            storeDir = Files.createTempDirectory("fix-store-");
            settings.setString("FileStorePath", storeDir.toString());
            settings.setString("FileStoreSync", "N");
            storeFactory = new FileStoreFactory(settings);
        } else {
            storeFactory = new MemoryStoreFactory();
        }
        settings.setString(INITIATOR_ID, "ConnectionType", "initiator");
        settings.setString(ACCEPTOR_ID, "ConnectionType", "acceptor");
        LogFactory logFactory = id -> new TraceLog(id.equals(INITIATOR_ID) ? Side.INITIATOR : Side.ACCEPTOR);
        for (Side side : Side.values()) {
            SessionID id = side == Side.INITIATOR ? INITIATOR_ID : ACCEPTOR_ID;
            DefaultSessionFactory factory = new DefaultSessionFactory(new RecordingApplication(side),
                    storeFactory, logFactory, new DefaultMessageFactory());
            try {
                sessions.put(side, factory.create(id, settings));
            } catch (quickfix.ConfigError e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private void finish() {
        trace.settled = settled();
        trace.simulatedMillis = clock.elapsedMillis();
        for (Side side : Side.values()) {
            Session s = session(side);
            trace.finalState.put(side, new RunTrace.FinalState(s.isLoggedOn(), s.getExpectedSenderNum(),
                    s.getExpectedTargetNum(), resendRequested(s)));
        }
    }

    private static boolean resendRequested(Session s) {
        try {
            Field f = Session.class.getDeclaredField("state");
            f.setAccessible(true);
            return ((quickfix.SessionState) f.get(s)).isResendRequested();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private void log(Side side, String text) {
        if (captureLog) {
            trace.events.add(String.format("%7.1fs %s %s", clock.elapsedMillis() / 1000.0,
                    side == null ? "-" : side.shortName(), text));
        }
    }

    @Override
    public void close() {
        for (Session s : sessions.values()) {
            try {
                s.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
        transport.disconnect();
        SimClock.uninstall();
        if (storeDir != null) {
            try (Stream<Path> files = Files.walk(storeDir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    /**
     * The fault-injecting {@link Responder}: counts every outbound send across both sides and
     * cuts the connection at the sends named by the schedule.
     */
    private final class Shim implements Responder {
        private final Side side;
        private final int connectionId;

        Shim(Side side, int connectionId) {
            this.side = side;
            this.connectionId = connectionId;
        }

        @Override
        public boolean send(String data) {
            if (!linkUp || connectionId != Scenario.this.connectionId) {
                // the peer already closed; a local socket write still succeeds, the bytes are lost
                return true;
            }
            int index = ++sendCounter;
            CutMode cut = schedule.faultAt(index);
            String received;
            try {
                received = transport.transmit(side, data, cut);
            } catch (Exception e) {
                throw new IllegalStateException("transport failed at send #" + index, e);
            }
            boolean transmitted = cut == null || cut == CutMode.AFTER;
            trace.outbound.add(new OutboundRecord(index, side, connectionId, FixFrames.intField(data, 34),
                    FixFrames.field(data, 35), "Y".equals(FixFrames.field(data, 43)),
                    FixFrames.intField(data, 7), cut, transmitted, received != null));
            log(side, "send #" + index + " " + FixMsgTypes.name(FixFrames.field(data, 35))
                    + " seq " + FixFrames.field(data, 34) + (cut == null ? "" : " CUT " + cut));
            if (received != null) {
                events.addLast(new Delivery(side.other(), connectionId, received));
            }
            if (cut == null) {
                return true;
            }
            log(side, "cut " + cut + " at send #" + index + " " + FixMsgTypes.name(FixFrames.field(data, 35)));
            dropLink(side, "Connection reset by peer");
            if (cut == CutMode.AFTER) {
                // the write completed; the I/O thread notices the drop right after
                events.addFirst(new Eof(side, connectionId, "Connection dropped after write"));
                return true;
            }
            // the I/O thread processed the drop first: the responder is gone when the write happens
            try {
                session(side).disconnect("Connection dropped before write", true);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return false;
        }

        @Override
        public void disconnect() {
            if (linkUp && connectionId == Scenario.this.connectionId) {
                dropLink(side, "Encountered END_OF_STREAM");
            }
        }

        @Override
        public String getRemoteAddress() {
            return "127.0.0.1:" + (20000 + connectionId);
        }
    }

    private void dropLink(Side closer, String reasonForPeer) {
        linkUp = false;
        transport.disconnect();
        events.addLast(new Eof(closer.other(), connectionId, reasonForPeer));
    }

    /** Records what each side's application layer receives. */
    private final class RecordingApplication implements Application {
        private final Side side;

        RecordingApplication(Side side) {
            this.side = side;
        }

        @Override
        public void onCreate(SessionID sessionId) {
        }

        @Override
        public void onLogon(SessionID sessionId) {
            log(side, "onLogon");
        }

        @Override
        public void onLogout(SessionID sessionId) {
            log(side, "onLogout");
        }

        @Override
        public void toAdmin(Message message, SessionID sessionId) {
        }

        @Override
        public void fromAdmin(Message message, SessionID sessionId) throws quickfix.FieldNotFound {
            if ("4".equals(message.getHeader().getString(35)) && message.isSetField(123)
                    && message.getBoolean(123)) {
                trace.gapFills.get(side).add(new RunTrace.GapFill(message.getHeader().getInt(34),
                        message.getInt(36)));
            }
        }

        @Override
        public void toApp(Message message, SessionID sessionId) {
        }

        @Override
        public void fromApp(Message message, SessionID sessionId) throws quickfix.FieldNotFound {
            Message.Header h = message.getHeader();
            boolean possDup = h.isSetField(43) && h.getBoolean(43);
            trace.received.get(side).add(new RunTrace.Received(h.getInt(34), message.getString(11), possDup));
        }
    }

    private final class TraceLog implements Log {
        private final Side side;

        TraceLog(Side side) {
            this.side = side;
        }

        @Override
        public void clear() {
        }

        @Override
        public void onIncoming(String message) {
        }

        @Override
        public void onOutgoing(String message) {
        }

        @Override
        public void onEvent(String text) {
            log(side, text);
        }

        @Override
        public void onErrorEvent(String text) {
            log(side, "ERROR " + text);
        }
    }
}
