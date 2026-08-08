package fixresend;

/** Which QuickFIX/J build and setting combination is being exercised (for reports only). */
public enum Build {
    /** Unmodified QuickFIX/J 2.3.2, SendRedundantResendRequests=N. */
    STOCK,
    /** Unmodified QuickFIX/J 2.3.2 with the SendRedundantResendRequests=Y workaround. */
    WORKAROUND,
    /** QuickFIX/J 2.3.2 with the #1114 fix, SendRedundantResendRequests=N. */
    PATCHED;

    public boolean redundantResends() {
        return this == WORKAROUND;
    }

    /** True if the quickfix.Session loaded in this JVM comes from outside the stock quickfixj-core jar. */
    public static boolean patchedSessionOnClasspath() {
        var src = quickfix.Session.class.getProtectionDomain().getCodeSource();
        return src != null && !src.getLocation().toString().contains("quickfixj-core");
    }
}
