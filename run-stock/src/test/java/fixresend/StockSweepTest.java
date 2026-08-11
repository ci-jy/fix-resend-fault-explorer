package fixresend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Exhaustive single and double disconnect sweeps against unmodified QuickFIX/J 2.3.2. */
class StockSweepTest {

    @Test
    void stockSweepFindsLosingSchedulesIncludingSingleResendRequestCut() {
        List<Outcome> all = Explorer.sweep(Build.STOCK, 3, 2, List.of(StoreType.MEMORY, StoreType.FILE));
        long losing = all.stream().filter(Outcome::losesOrDuplicates).count();
        System.out.printf("stock: %d schedules, %d lose messages%n", all.size(), losing);
        assertTrue(all.size() > 3000);
        assertTrue(losing > 0);
        assertTrue(all.stream().anyMatch(o -> o.schedule().size() == 1 && o.losesOrDuplicates()
                && o.faults().contains("ResendRequest") && o.faults().endsWith("BEFORE")));
    }

    @Test
    void workaroundLosesNothingButFloodsDuringBurst() {
        List<Outcome> all = Explorer.sweep(Build.WORKAROUND, 3, 2, List.of(StoreType.MEMORY));
        assertTrue(all.stream().noneMatch(Outcome::losesOrDuplicates));
        List<Outcome> burst = new java.util.ArrayList<>();
        Explorer.explore(Build.WORKAROUND, new Config(2, true, StoreType.MEMORY, 5), 1, burst::add);
        int dup = burst.stream().mapToInt(Outcome::duplicateResendRequests).sum();
        System.out.printf("workaround burst: %d duplicate ResendRequests over %d schedules%n", dup, burst.size());
        assertTrue(dup >= burst.size());
    }
}
