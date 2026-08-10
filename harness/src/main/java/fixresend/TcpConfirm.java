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
}
