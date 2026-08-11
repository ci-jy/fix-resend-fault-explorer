package quickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;

import quickfix.field.BeginSeqNo;
import quickfix.field.ClOrdID;
import quickfix.field.EncryptMethod;
import quickfix.field.HeartBtInt;
import quickfix.field.MsgSeqNum;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.SendingTime;
import quickfix.field.TargetCompID;

/**
 * Regression test for issue #1114: a ResendRequest that fails to send because the connection
 * dropped (responder cleared by a concurrent disconnect) must not be recorded as sent, otherwise
 * the gap is never requested again after the next Logon and the missing messages are lost.
 */
public class SessionResendRequestNotSentTest {

    private final SessionID sessionID = new SessionID("FIX.4.4", "BROKER", "CLIENT");
    private Session session;

    /** Records sends; optionally drops the connection when a ResendRequest is written. */
    private static final class DroppingResponder implements Responder {
        final List<String> sent = new ArrayList<>();
        final boolean dropOnResendRequest;
        Session session;

        DroppingResponder(boolean dropOnResendRequest) {
            this.dropOnResendRequest = dropOnResendRequest;
        }

        @Override
        public boolean send(String data) {
            if (dropOnResendRequest && data.contains("\u000135=2\u0001")) {
                try {
                    // the I/O thread processed the disconnect first: the write cannot happen
                    session.disconnect("connection dropped", false);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return false;
            }
            sent.add(data);
            return true;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public String getRemoteAddress() {
            return "127.0.0.1:0";
        }

        List<String> resendRequests() {
            return sent.stream().filter(s -> s.contains("\u000135=2\u0001")).collect(Collectors.toList());
        }
    }

    @After
    public void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
    }

    private Message incoming(String msgType, int seqNum) {
        Message m = new DefaultMessageFactory().create("FIX.4.4", msgType);
        m.getHeader().setString(SenderCompID.FIELD, "CLIENT");
        m.getHeader().setString(TargetCompID.FIELD, "BROKER");
        m.getHeader().setInt(MsgSeqNum.FIELD, seqNum);
        m.getHeader().setUtcTimeStamp(SendingTime.FIELD, LocalDateTime.now(ZoneOffset.UTC));
        if (MsgType.LOGON.equals(msgType)) {
            m.setInt(EncryptMethod.FIELD, 0);
            m.setInt(HeartBtInt.FIELD, 30);
        } else {
            m.setString(ClOrdID.FIELD, "order-" + seqNum);
            m.setChar(54, '1');
            m.setString(55, "SYM");
            m.setChar(40, '1');
            m.setUtcTimeStamp(60, LocalDateTime.now(ZoneOffset.UTC));
        }
        return m;
    }

    @Test
    public void failedResendRequestIsSentAgainAfterRelogon() throws Exception {
        SessionSettings settings = new SessionSettings();
        settings.setString(sessionID, "ConnectionType", "acceptor");
        settings.setString(sessionID, "NonStopSession", "Y");
        settings.setString(sessionID, "UseDataDictionary", "N");
        settings.setString(sessionID, "SocketAcceptPort", "20001");
        session = new DefaultSessionFactory(new ApplicationAdapter(), new MemoryStoreFactory(), null,
                new DefaultMessageFactory()).create(sessionID, settings);

        DroppingResponder first = new DroppingResponder(true);
        first.session = session;
        session.setResponder(first);
        session.next(incoming(MsgType.LOGON, 1));
        assertTrue(session.isLoggedOn());

        // seq 2 is missing: the ResendRequest for it fails because the connection drops
        session.next(incoming(MsgType.ORDER_SINGLE, 3));
        assertTrue(!session.hasResponder());

        // reconnect: the client's Logon (seq 4) shows the gap is still open
        DroppingResponder second = new DroppingResponder(false);
        session.setResponder(second);
        session.next(incoming(MsgType.LOGON, 4));

        List<String> requests = second.resendRequests();
        assertEquals("a ResendRequest must be sent on the new connection", 1, requests.size());
        Message rr = new Message(requests.get(0));
        assertEquals(2, rr.getInt(BeginSeqNo.FIELD));
    }
}
