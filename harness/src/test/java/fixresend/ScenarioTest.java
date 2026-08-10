package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScenarioTest {
    @Test
    void faultFreeRunDeliversEverythingAndCoversAdminTypes() {
        RunTrace t = Scenario.run(new Config(3, false, StoreType.MEMORY), Schedule.NONE);
        assertEquals(List.of(), ReferenceModel.check(t));
        assertTrue(t.settled);
        Set<String> types = t.outbound.stream().map(OutboundRecord::typeName).collect(Collectors.toSet());
        assertTrue(types.containsAll(Set.of("Logon", "Heartbeat", "TestRequest", "Logout", "ResendRequest",
                "SequenceReset", "NewOrderSingle")), types.toString());
    }

    @Test
    void runsAreDeterministic() {
        Config c = new Config(2, false, StoreType.FILE);
        Schedule s = Schedule.parse("4:BEFORE 9:AFTER");
        assertEquals(Scenario.run(c, s).outbound, Scenario.run(c, s).outbound);
    }

    @Test
    void explorerPrunesSecondFaultsBeyondTheRun() {
        Config c = new Config(1, false, StoreType.MEMORY);
        List<Outcome> all = new ArrayList<>();
        int runs = Explorer.explore(Build.STOCK, c, 2, all::add);
        int base = Scenario.run(c, Schedule.NONE).totalSends();
        assertEquals(runs, all.size());
        assertEquals(1 + 2 * base, all.stream().filter(o -> o.schedule().size() <= 1).count());
        assertTrue(all.stream().allMatch(o -> o.schedule().faults().stream()
                .allMatch(f -> f.sendIndex() <= o.totalSends())));
    }
}
