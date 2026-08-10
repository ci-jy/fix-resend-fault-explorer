package fixresend;

/** Prints the event log and outcome of one schedule: {@code Debug <m> <redundant Y|N> <store> <schedule...>}. */
public final class Debug {
    private Debug() {
    }

    public static void main(String[] args) {
        int burst = 0;
        int from = 3;
        if (args.length > 3 && args[3].startsWith("burst=")) {
            burst = Integer.parseInt(args[3].substring(6));
            from = 4;
        }
        Config config = new Config(Integer.parseInt(args[0]), args[1].equals("Y"), StoreType.valueOf(args[2]), burst);
        Schedule schedule = Schedule.parse(String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)));
        RunTrace t;
        try (Scenario s = new Scenario(config, schedule, new InJvmTransport(), true)) {
            t = s.execute();
        }
        t.events.forEach(System.out::println);
        for (OutboundRecord o : t.outbound) {
            System.out.println(o);
        }
        System.out.println("faults: " + t.describeFaults());
        System.out.println("received: " + t.received);
        System.out.println("final: " + t.finalState + " settled=" + t.settled);
        System.out.println("violations: " + ReferenceModel.check(t));
    }
}
