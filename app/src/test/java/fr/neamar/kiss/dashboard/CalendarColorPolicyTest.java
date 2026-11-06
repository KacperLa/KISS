package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CalendarColorPolicyTest {
    @Test void usesEventThenCalendarThenNeutralFallback() {
        assertEquals(0xff123456, CalendarColorPolicy.resolve(0xff123456, 0xff654321, 0xffaaaaaa));
        assertEquals(0xff654321, CalendarColorPolicy.resolve(null, 0xff654321, 0xffaaaaaa));
        assertEquals(0xffaaaaaa, CalendarColorPolicy.resolve(null, null, 0xffaaaaaa));
        assertEquals(0xff000000, CalendarColorPolicy.resolve(0xff000000, 0xff654321, 0xffaaaaaa));
    }
}

