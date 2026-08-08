package fixresend;

import quickfix.SystemTime;
import quickfix.SystemTimeSource;

/** Deterministic clock installed into QuickFIX/J so timers are driven by the harness. */
public final class SimClock implements SystemTimeSource {
    /** 2026-01-05T10:00:00Z, a Monday morning. */
    public static final long START = 1767607200000L;

    private long now = START;

    @Override
    public long getTime() {
        return now;
    }

    @Override
    public java.time.LocalDateTime getNow() {
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneOffset.UTC);
    }

    public void advance(long millis) {
        now += millis;
    }

    public long elapsedMillis() {
        return now - START;
    }

    public void install() {
        SystemTime.setTimeSource(this);
    }

    public static void uninstall() {
        SystemTime.setTimeSource(null);
    }
}
