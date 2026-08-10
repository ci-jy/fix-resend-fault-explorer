package fixresend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges per-build CSV parts into results/schedules.csv and results/summary.md.
 *
 * <pre>SummaryCli --out-dir results [--check] part1.csv part2.csv ...</pre>
 *
 * With {@code --check} the exit code is non-zero unless: the stock build loses messages, including
 * through a single disconnect at a ResendRequest; the patched build loses and duplicates nothing;
 * and the workaround sends more duplicate ResendRequests than the patch.
 */
public final class SummaryCli {
    private SummaryCli() {
    }

    /** One parsed CSV row. */
    record Row(String build, String store, String redundant, int m, int burst, int disconnects, String schedule,
               String faults, int sends, int lost, int dup, int ooo, int gap, int notRecovered, int rr, int dupRr,
               String violations) {
        static Row parse(String line) {
            String[] f = line.split(",", -1);
            return new Row(f[0], f[1], f[2], Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    f[6], f[7], Integer.parseInt(f[8]), Integer.parseInt(f[9]), Integer.parseInt(f[10]),
                    Integer.parseInt(f[11]), Integer.parseInt(f[12]), Integer.parseInt(f[13]), Integer.parseInt(f[14]),
                    Integer.parseInt(f[15]), f[16]);
        }

        boolean losesOrDuplicates() {
            return lost > 0 || dup > 0;
        }
    }

    /** Per-build totals. */
    static final class Totals {
        int schedules;
        int losing;
        int duplicating;
        int notRecovered;
        int lostMessages;
        int duplicatedMessages;
        int burstSchedules;
        int burstRr;
        int burstDupRr;
        int rr;
        int dupRr;
    }

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of("results");
        boolean check = false;
        List<Path> parts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out-dir" -> outDir = Path.of(args[++i]);
                case "--check" -> check = true;
                default -> parts.add(Path.of(args[i]));
            }
        }
        Files.createDirectories(outDir);
        List<Row> rows = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        lines.add(Outcome.CSV_HEADER);
        for (Path p : parts) {
            List<String> l = Files.readAllLines(p);
            for (String line : l.subList(1, l.size())) {
                lines.add(line);
                rows.add(Row.parse(line));
            }
        }
        Files.write(outDir.resolve("schedules.csv"), lines);

        Map<String, Totals> totals = new LinkedHashMap<>();
        for (Row r : rows) {
            Totals t = totals.computeIfAbsent(r.build(), k -> new Totals());
            t.schedules++;
            t.losing += r.lost() > 0 ? 1 : 0;
            t.duplicating += r.dup() > 0 ? 1 : 0;
            t.notRecovered += r.notRecovered() > 0 ? 1 : 0;
            t.lostMessages += r.lost();
            t.duplicatedMessages += r.dup();
            t.rr += r.rr();
            t.dupRr += r.dupRr();
            if (r.burst() > 0) {
                t.burstSchedules++;
                t.burstRr += r.rr();
                t.burstDupRr += r.dupRr();
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Disconnect-point exploration summary\n\n");
        md.append("QuickFIX/J 2.3.2, FIX.4.4, every single and double disconnect point (cut before or after each "
                + "outbound send, both sides), message stores: ")
                .append(rows.stream().map(Row::store).distinct().toList()).append(", up to ")
                .append(rows.stream().mapToInt(Row::m).max().orElse(0)).append(" application messages per side")
                .append(" plus a ").append(ExplorerCli.BURST).append("-message congested burst.\n\n");
        md.append("| Build | Schedules explored | Losing messages | Duplicating messages | Not recovered | "
                + "Lost messages (total) | ResendRequests sent | Duplicate ResendRequests | "
                + "Duplicate ResendRequests (burst runs) |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (Map.Entry<String, Totals> e : totals.entrySet()) {
            Totals t = e.getValue();
            md.append("| ").append(label(e.getKey())).append(" | ").append(t.schedules).append(" | ").append(t.losing)
                    .append(" | ").append(t.duplicating).append(" | ").append(t.notRecovered).append(" | ")
                    .append(t.lostMessages).append(" | ").append(t.rr).append(" | ").append(t.dupRr).append(" | ")
                    .append(t.burstDupRr).append(" / ").append(t.burstSchedules).append(" runs |\n");
        }
        md.append("\nA schedule is a list of `send#:MODE` disconnect points; send numbers count outbound sends "
                + "across both sessions. BEFORE = the drop is processed before the write (Responder.send returns "
                + "false), AFTER = the write is delivered, then the connection drops.\n");

        md.append("\n## Minimal reproducing schedules\n\n");
        md.append("Shortest schedule (fewest disconnects, then fewest application messages) per build and "
                + "violation kind.\n\n| Build | Violation | Store | m | Schedule | Faulted sends | Violations |\n"
                + "|---|---|---|---:|---|---|---|\n");
        Comparator<Row> shortest = Comparator.comparingInt(Row::disconnects).thenComparingInt(Row::burst)
                .thenComparingInt(Row::m).thenComparing(Row::store).thenComparing(Row::schedule);
        for (String build : totals.keySet()) {
            for (Violation.Kind kind : Violation.Kind.values()) {
                rows.stream().filter(r -> r.build().equals(build)).filter(r -> has(r, kind)).min(shortest)
                        .ifPresent(r -> md.append("| ").append(label(build)).append(" | ").append(kind).append(" | ")
                                .append(r.store()).append(" | ").append(r.m()).append(" | `").append(r.schedule())
                                .append("` | ").append(r.faults()).append(" | ").append(r.violations()).append(" |\n"));
            }
        }

        md.append("\n## Losing schedules by faulted message (stock)\n\n| Faulted message(s) | Losing schedules |\n|---|---:|\n");
        Map<String, Integer> byMsg = new java.util.TreeMap<>();
        rows.stream().filter(r -> r.build().equals("STOCK") && r.lost() > 0).forEach(r ->
                byMsg.merge(r.faults().replaceAll("#\\d+ ", "").replaceAll("\\(\\d+\\)", ""), 1, Integer::sum));
        byMsg.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(15)
                .forEach(e -> md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n"));

        boolean singleRr = rows.stream().anyMatch(r -> r.build().equals("STOCK") && r.disconnects() == 1
                && r.lost() > 0 && r.faults().contains("ResendRequest") && r.faults().endsWith("BEFORE"));
        Totals stock = totals.get("STOCK");
        Totals patched = totals.get("PATCHED");
        Totals workaround = totals.get("WORKAROUND");
        List<String> failures = new ArrayList<>();
        if (stock == null || stock.losing == 0) {
            failures.add("stock build shows no losing schedule");
        }
        if (!singleRr) {
            failures.add("stock build lacks the single-disconnect ResendRequest case");
        }
        if (patched == null || patched.losing > 0 || patched.duplicating > 0) {
            failures.add("patched build loses or duplicates messages");
        }
        if (workaround == null || patched == null || workaround.burstDupRr <= patched.burstDupRr) {
            failures.add("workaround does not send more duplicate ResendRequests than the patch");
        }
        md.append("\n## Checks\n\n");
        md.append("- Stock build reproduces #1114 with a single disconnect at a ResendRequest: ")
                .append(singleRr ? "yes" : "NO").append('\n');
        md.append("- Patched build loses or duplicates no application message: ")
                .append(patched != null && patched.losing == 0 && patched.duplicating == 0 ? "yes" : "NO").append('\n');
        md.append("- Workaround sends more duplicate ResendRequests than the patch: ")
                .append(failures.stream().anyMatch(f -> f.startsWith("workaround")) ? "NO" : "yes").append('\n');
        Files.writeString(outDir.resolve("summary.md"), md.toString());
        System.out.print(md);
        if (check && !failures.isEmpty()) {
            System.err.println("CHECK FAILED: " + failures);
            System.exit(1);
        }
    }

    private static boolean has(Row r, Violation.Kind k) {
        return switch (k) {
            case LOST -> r.lost() > 0;
            case DUPLICATED -> r.dup() > 0;
            case OUT_OF_ORDER -> r.ooo() > 0;
            case APP_GAP_FILLED -> r.gap() > 0;
            case NOT_RECOVERED -> r.notRecovered() > 0;
        };
    }

    private static String label(String build) {
        return switch (build) {
            case "STOCK" -> "stock 2.3.2";
            case "WORKAROUND" -> "stock + SendRedundantResendRequests=Y";
            case "PATCHED" -> "patched (#1114 fix)";
            default -> build;
        };
    }
}
