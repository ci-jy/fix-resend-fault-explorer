package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScheduleTest {
    @Test
    void parsesAndPrintsRoundTrip() {
        Schedule s = Schedule.parse("4:BEFORE 15:AFTER");
        assertEquals("4:BEFORE 15:AFTER", s.toString());
        assertEquals(CutMode.AFTER, s.faultAt(15));
        assertNull(s.faultAt(5));
        assertEquals(Schedule.NONE, Schedule.parse("-"));
    }

    @Test
    void rejectsNonIncreasingIndices() {
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("5:BEFORE 5:AFTER"));
    }

    @Test
    void midCutMapsToBeforeInJvm() {
        assertEquals(CutMode.BEFORE, CutMode.MID.inJvmEquivalent());
        assertEquals(CutMode.AFTER, CutMode.AFTER.inJvmEquivalent());
    }
}
