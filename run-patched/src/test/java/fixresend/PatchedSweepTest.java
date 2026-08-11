package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exhaustive sweeps against the patched build: nothing is lost or duplicated. */
class PatchedSweepTest {

    @Test
    void noLostOrDuplicatedMessagesAcrossEverySchedule() {
        List<Outcome> all = Explorer.sweep(Build.PATCHED, 5, 2, List.of(StoreType.MEMORY, StoreType.FILE));
        long lost = all.stream().mapToLong(o -> o.count(Violation.Kind.LOST)).sum();
        long dup = all.stream().mapToLong(o -> o.count(Violation.Kind.DUPLICATED)).sum();
        long gap = all.stream().mapToLong(o -> o.count(Violation.Kind.APP_GAP_FILLED)).sum();
        long ooo = all.stream().mapToLong(o -> o.count(Violation.Kind.OUT_OF_ORDER)).sum();
        System.out.printf("patched: %d schedules, %d lost, %d duplicated%n", all.size(), lost, dup);
        assertTrue(all.size() > 10000, "explored " + all.size());
        assertEquals(0, lost);
        assertEquals(0, dup);
        assertEquals(0, gap);
        assertEquals(0, ooo);
    }

    @Test
    void patchSendsNoDuplicateResendRequestsDuringBurst() {
        List<Outcome> burst = new ArrayList<>();
        Explorer.explore(Build.PATCHED, new Config(2, false, StoreType.MEMORY, 5), 2, burst::add);
        assertTrue(burst.stream().noneMatch(Outcome::losesOrDuplicates));
        assertEquals(0, burst.stream().mapToInt(Outcome::duplicateResendRequests).sum());
    }
}
