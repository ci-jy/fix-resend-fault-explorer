package fixresend;

import java.util.List;

/** The judged result of one schedule. */
public record Outcome(Build build, Config config, Schedule schedule, String faults, int totalSends,
                      List<Violation> violations, int resendRequests, int duplicateResendRequests) {

    public static Outcome of(Build build, Config config, RunTrace t) {
        return new Outcome(build, config, t.schedule, t.describeFaults(), t.totalSends(), ReferenceModel.check(t),
                t.resendRequestsSent(Side.INITIATOR) + t.resendRequestsSent(Side.ACCEPTOR),
                t.duplicateResendRequests());
    }

    public long count(Violation.Kind kind) {
        return violations.stream().filter(v -> v.kind() == kind).count();
    }

    public boolean losesOrDuplicates() {
        return ReferenceModel.losesOrDuplicates(violations);
    }

    public boolean ok() {
        return violations.isEmpty();
    }

    public static final String CSV_HEADER = "build,store,redundant_resends,app_messages,burst,disconnects,schedule,faulted_sends,"
            + "total_sends,lost,duplicated,out_of_order,app_gap_filled,not_recovered,resend_requests,"
            + "duplicate_resend_requests,violations";

    public String toCsv() {
        return String.join(",", build.name(), config.store().name(), config.redundantResends() ? "Y" : "N",
                Integer.toString(config.appMessages()), Integer.toString(config.burst()), Integer.toString(schedule.size()), schedule.toString(),
                clean(faults), Integer.toString(totalSends), Long.toString(count(Violation.Kind.LOST)),
                Long.toString(count(Violation.Kind.DUPLICATED)), Long.toString(count(Violation.Kind.OUT_OF_ORDER)),
                Long.toString(count(Violation.Kind.APP_GAP_FILLED)), Long.toString(count(Violation.Kind.NOT_RECOVERED)),
                Integer.toString(resendRequests), Integer.toString(duplicateResendRequests),
                clean(violations.stream().map(Violation::toString).reduce((a, b) -> a + "; " + b).orElse("")));
    }

    private static String clean(String s) {
        return s.replace(',', ' ');
    }
}
