package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationSwipePolicyTest {
    @Test void shortDragsSnapBackAndEitherDirectionCanDismiss() {
        assertFalse(NotificationSwipePolicy.shouldDismiss(71, 200, 72));
        assertTrue(NotificationSwipePolicy.shouldDismiss(72, 200, 72));
        assertTrue(NotificationSwipePolicy.shouldDismiss(-72, 200, 72));
        assertFalse(NotificationSwipePolicy.shouldDismiss(100, 800, 72));
        assertTrue(NotificationSwipePolicy.shouldDismiss(200, 800, 72));
    }
}
