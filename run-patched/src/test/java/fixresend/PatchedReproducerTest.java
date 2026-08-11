package fixresend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** QuickFIX/J 2.3.2 with the #1114 fix: the reproducer recovers every message. */
class PatchedReproducerTest {

    @Test
    void patchedSessionIsFirstOnClasspath() {
        assertTrue(Build.patchedSessionOnClasspath());
    }

    @Test
    void singleDisconnectAtResendRequestRecovers() {
        RunTrace t = Scenario.run(new Config(2, false, StoreType.MEMORY), Schedule.parse("15:BEFORE"));
        assertEquals("ResendRequest", t.send(15).typeName());
        assertEquals(List.of(), ReferenceModel.check(t));
    }

    @Test
    void singleDisconnectAtResendRequestRecoversWithFileStore() {
        RunTrace t = Scenario.run(new Config(2, false, StoreType.FILE), Schedule.parse("15:BEFORE"));
        assertEquals(List.of(), ReferenceModel.check(t));
    }
}
