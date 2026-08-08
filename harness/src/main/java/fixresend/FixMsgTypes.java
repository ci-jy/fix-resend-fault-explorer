package fixresend;

import java.util.Map;

/** Readable names for the FIX.4.4 message types the harness exchanges. */
public final class FixMsgTypes {
    private static final Map<String, String> NAMES = Map.of(
            "A", "Logon", "0", "Heartbeat", "1", "TestRequest", "2", "ResendRequest",
            "3", "Reject", "4", "SequenceReset", "5", "Logout", "D", "NewOrderSingle");

    private FixMsgTypes() {
    }

    public static String name(String msgType) {
        return NAMES.getOrDefault(msgType, msgType);
    }
}
