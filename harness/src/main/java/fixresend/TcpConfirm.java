package fixresend;

import java.util.ArrayList;
import java.util.List;

/** Re-runs schedules over real loopback TCP and compares them with the in-JVM result. */
public final class TcpConfirm {
    private TcpConfirm() {
    }

    /** Result of one TCP confirmation. */
    public record Comparison(Config config, Schedule tcpSchedule, List<Violation> inJvm, List<Violation> tcp,
                             int inJvmSends, int tcpSends, int partialFrames) {
        public boolean agrees() {
            return inJvm.equals(tcp) && inJvmSends == tcpSends;
        }
    }

    /** Runs {@code schedule} over TCP; MID cuts are compared with the BEFORE in-JVM schedule. */
    public static Comparison compare(Config config, Schedule schedule) throws Exception {
        List<Fault> jvmFaults = new ArrayList<>();
        for (Fault f : schedule.faults()) {
            jvmFaults.add(new Fault(f.sendIndex(), f.mode().inJvmEquivalent()));
        }
        RunTrace jvm = Scenario.run(config, new Schedule(jvmFaults));
        RunTrace tcp;
        int partial;
        try (TcpTransport transport = new TcpTransport();
             Scenario s = new Scenario(config, schedule, transport, false)) {
            tcp = s.execute();
            partial = transport.partialFrames();
        }
        return new Comparison(config, schedule, ReferenceModel.check(jvm), ReferenceModel.check(tcp),
                jvm.totalSends(), tcp.totalSends(), partial);
    }

    /**
     * A deterministic sample of {@code n} schedules from the full exploration of {@code config}
     * (always including the first single-disconnect ResendRequest cut), with every other BEFORE
     * cut turned into a mid-frame cut.
     */
    public static List<Schedule> sample(Build build, Config config, int n) {
        List<Outcome> all = new ArrayList<>();
        Explorer.explore(build, config, 2, all::add);
        List<Schedule> out = new ArrayList<>();
        all.stream().filter(o -> o.schedule().size() == 1 && o.faults().contains("ResendRequest")
                && o.faults().endsWith("BEFORE")).findFirst().ifPresent(o -> out.add(o.schedule()));
        int step = Math.max(1, all.size() / n);
        for (int i = 0; out.size() < n && i < all.size(); i += step) {
            List<Fault> faults = new ArrayList<>();
            for (Fault f : all.get(i).schedule().faults()) {
                faults.add(f.mode() == CutMode.BEFORE && out.size() % 2 == 0 ? new Fault(f.sendIndex(), CutMode.MID) : f);
            }
            out.add(new Schedule(faults));
        }
        return out;
    }
}
