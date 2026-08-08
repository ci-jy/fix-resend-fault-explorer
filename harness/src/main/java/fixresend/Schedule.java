package fixresend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** An ordered set of disconnect points. Indices are strictly increasing. */
public record Schedule(List<Fault> faults) {
    public static final Schedule NONE = new Schedule(List.of());

    public Schedule {
        faults = List.copyOf(faults);
        for (int i = 1; i < faults.size(); i++) {
            if (faults.get(i).sendIndex() <= faults.get(i - 1).sendIndex()) {
                throw new IllegalArgumentException("fault indices must increase: " + faults);
            }
        }
    }

    public static Schedule of(Fault... faults) {
        return new Schedule(Arrays.asList(faults));
    }

    public Schedule plus(Fault f) {
        List<Fault> l = new ArrayList<>(faults);
        l.add(f);
        return new Schedule(l);
    }

    public CutMode faultAt(int sendIndex) {
        for (Fault f : faults) {
            if (f.sendIndex() == sendIndex) {
                return f.mode();
            }
        }
        return null;
    }

    public int size() {
        return faults.size();
    }

    @Override
    public String toString() {
        return faults.isEmpty() ? "-" : faults.stream().map(Fault::toString).collect(Collectors.joining(" "));
    }

    public static Schedule parse(String s) {
        if (s == null || s.isBlank() || s.equals("-")) {
            return NONE;
        }
        return new Schedule(Arrays.stream(s.trim().split("\\s+")).map(Fault::parse).toList());
    }
}
