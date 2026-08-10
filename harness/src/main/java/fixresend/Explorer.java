package fixresend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exhaustively explores disconnect points. Every outbound send of a run (admin or application,
 * either side) is a candidate disconnect point, cut BEFORE or AFTER the write.
 *
 * <p>Deduplication: runs are deterministic, so a schedule with faults at k1 &lt; k2 behaves
 * exactly like the single-fault schedule k1 up to send k2. Second faults are therefore only
 * tried at indices that exist in the k1 run; any later index would never fire and is the same
 * schedule as k1 alone.
 */
public final class Explorer {
    public static final CutMode[] MODES = {CutMode.BEFORE, CutMode.AFTER};

    private Explorer() {
    }

    /** Explores all schedules with up to {@code maxDisconnects} (1 or 2) faults for one config. */
    public static int explore(Build build, Config config, int maxDisconnects, Consumer<Outcome> sink) {
        int runs = 0;
        RunTrace base = Scenario.run(config, Schedule.NONE);
        sink.accept(Outcome.of(build, config, base));
        runs++;
        if (maxDisconnects < 1) {
            return runs;
        }
        for (int k1 = 1; k1 <= base.totalSends(); k1++) {
            for (CutMode m1 : MODES) {
                Schedule s1 = Schedule.of(new Fault(k1, m1));
                RunTrace t1 = Scenario.run(config, s1);
                sink.accept(Outcome.of(build, config, t1));
                runs++;
                if (maxDisconnects < 2) {
                    continue;
                }
                for (int k2 = k1 + 1; k2 <= t1.totalSends(); k2++) {
                    for (CutMode m2 : MODES) {
                        sink.accept(Outcome.of(build, config, Scenario.run(config, s1.plus(new Fault(k2, m2)))));
                        runs++;
                    }
                }
            }
        }
        return runs;
    }

    /** Sweeps app-message counts 1..maxAppMessages and every store for a build. */
    public static List<Outcome> sweep(Build build, int maxAppMessages, int maxDisconnects, List<StoreType> stores) {
        List<Outcome> all = new ArrayList<>();
        for (StoreType store : stores) {
            for (int m = 1; m <= maxAppMessages; m++) {
                explore(build, new Config(m, build.redundantResends(), store), maxDisconnects, all::add);
            }
        }
        return all;
    }
}
