package fixresend;

/**
 * Session settings for one exploration.
 *
 * @param appMessages application messages sent by each side
 * @param redundantResends value of SendRedundantResendRequests
 * @param store message store type
 */
public record Config(int appMessages, boolean redundantResends, StoreType store) {
    @Override
    public String toString() {
        return "m=" + appMessages + " redundant=" + (redundantResends ? "Y" : "N") + " store=" + store;
    }
}
