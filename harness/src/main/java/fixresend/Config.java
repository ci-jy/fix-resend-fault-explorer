package fixresend;

/**
 * Session settings for one exploration.
 *
 * @param appMessages application messages sent by each side
 * @param redundantResends value of SendRedundantResendRequests
 * @param store message store type
 * @param burst extra acceptor messages streamed while a ResendRequest from the initiator is
 *              still unanswered (0 = none); measures redundant ResendRequests
 */
public record Config(int appMessages, boolean redundantResends, StoreType store, int burst) {
    public Config(int appMessages, boolean redundantResends, StoreType store) {
        this(appMessages, redundantResends, store, 0);
    }

    @Override
    public String toString() {
        return "m=" + appMessages + " redundant=" + (redundantResends ? "Y" : "N") + " store=" + store
                + (burst > 0 ? " burst=" + burst : "");
    }
}
