package fixresend;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the exhaustive sweep for one build in this JVM and writes one CSV line per schedule.
 *
 * <pre>
 * ExplorerCli --build STOCK|WORKAROUND|PATCHED --max-app-messages N --disconnects D
 *             [--stores MEMORY,FILE] --out file.csv
 * </pre>
 */
public final class ExplorerCli {
    /** Size of the congested burst used to count redundant ResendRequests. */
    public static final int BURST = 5;

    private ExplorerCli() {
    }

    public static void main(String[] args) throws IOException {
        Build build = Build.STOCK;
        int maxApp = 4;
        int disconnects = 2;
        List<StoreType> stores = List.of(StoreType.MEMORY, StoreType.FILE);
        Path out = Path.of("results", "schedules-part.csv");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--build" -> build = Build.valueOf(args[++i].toUpperCase());
                case "--max-app-messages" -> maxApp = Integer.parseInt(args[++i]);
                case "--disconnects" -> disconnects = Integer.parseInt(args[++i]);
                case "--stores" -> stores = Arrays.stream(args[++i].split(",")).map(String::trim)
                        .map(String::toUpperCase).map(StoreType::valueOf).toList();
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        boolean patched = Build.patchedSessionOnClasspath();
        if (patched != (build == Build.PATCHED)) {
            throw new IllegalStateException("build " + build + " needs the " + (patched ? "stock" : "patched")
                    + " quickfix.Session on the classpath");
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        long start = System.nanoTime();
        int[] counts = new int[2];
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println(Outcome.CSV_HEADER);
            for (StoreType store : stores) {
                java.util.function.Consumer<Outcome> sink = o -> {
                    w.println(o.toCsv());
                    counts[0]++;
                    if (o.losesOrDuplicates()) {
                        counts[1]++;
                    }
                };
                for (int m = 1; m <= maxApp; m++) {
                    Explorer.explore(build, new Config(m, build.redundantResends(), store), disconnects, sink);
                }
                Explorer.explore(build, new Config(2, build.redundantResends(), store, BURST), disconnects, sink);
            }
        }
        System.out.printf("%s: %d schedules explored, %d lose or duplicate messages (%.1f s) -> %s%n",
                build, counts[0], counts[1], (System.nanoTime() - start) / 1e9, out);
    }
}
