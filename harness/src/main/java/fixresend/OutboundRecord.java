package fixresend;

/**
 * One outbound send observed by the fault shim.
 *
 * @param sendIndex global 1-based index of the send (fault points refer to it)
 * @param cut the cut applied at this send, or null
 * @param transmitted true if the bytes were handed to the wire (send returned true)
 * @param delivered true if the peer received the complete frame
 */
public record OutboundRecord(int sendIndex, Side side, int connectionId, int seqNum, String msgType,
                             boolean possDup, int beginSeqNo, CutMode cut, boolean transmitted,
                             boolean delivered) {

    public String typeName() {
        return FixMsgTypes.name(msgType);
    }
}
