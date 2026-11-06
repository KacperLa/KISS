package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationLaunchPolicyTest {
    @Test void onlyLiveForegroundLauncherMaySend() {
        assertTrue(NotificationLaunchPolicy.canSend(true, false, false));
        assertFalse(NotificationLaunchPolicy.canSend(false, false, false));
        assertFalse(NotificationLaunchPolicy.canSend(true, true, false));
        assertFalse(NotificationLaunchPolicy.canSend(true, false, true));
        assertFalse(NotificationLaunchPolicy.canSend(false, true, true));
    }
}
