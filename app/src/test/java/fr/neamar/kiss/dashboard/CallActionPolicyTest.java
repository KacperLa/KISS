package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CallActionPolicyTest {
    @Test void requiresCallMetadataAndExactKnownSemantics() {
        assertEquals(CallActionPolicy.Kind.TEXT, CallActionPolicy.classify(false, "Hang up"));
        assertEquals(CallActionPolicy.Kind.HANG_UP, CallActionPolicy.classify(true, "End call"));
        assertEquals(CallActionPolicy.Kind.MUTE, CallActionPolicy.classify(true, "Mute"));
        assertEquals(CallActionPolicy.Kind.UNMUTE, CallActionPolicy.classify(true, "Unmute"));
        for (String label : new String[]{"Reject", "Decline", "Don't hang up", "Mute notifications", "Beenden", ""})
            assertEquals(CallActionPolicy.Kind.TEXT, CallActionPolicy.classify(true, label));
        assertEquals(CallActionPolicy.Kind.TEXT, CallActionPolicy.classify(true, null));
    }
}
