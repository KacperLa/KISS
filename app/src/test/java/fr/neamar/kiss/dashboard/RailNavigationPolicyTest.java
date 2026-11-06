package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RailNavigationPolicyTest {
    @Test void visibleEndpointLettersSelectTheirOwnBands() {
        assertEquals(3, RailNavigationPolicy.index(162, 48, 96, 144, 444, 18));
        assertEquals(28, RailNavigationPolicy.index(426, 48, 96, 144, 444, 18));
        assertEquals(3, RailNavigationPolicy.index(144, 48, 96, 144, 444, 18));
        assertEquals(2, RailNavigationPolicy.index(143, 48, 96, 144, 444, 18));
    }
    @Test void crossesEverySectionAndAlphabetWithoutNewPointer() {
        assertEquals(0, RailNavigationPolicy.index(-20, 48, 96, 144, 404));
        assertEquals(0, RailNavigationPolicy.index(47, 48, 96, 144, 404));
        assertEquals(1, RailNavigationPolicy.index(48, 48, 96, 144, 404));
        assertEquals(2, RailNavigationPolicy.index(96, 48, 96, 144, 404));
        assertEquals(3, RailNavigationPolicy.index(144, 48, 96, 144, 404));
        assertEquals(4, RailNavigationPolicy.index(154, 48, 96, 144, 404));
        assertEquals(28, RailNavigationPolicy.index(800, 48, 96, 144, 404));
        assertEquals(2, RailNavigationPolicy.index(143, 48, 96, 144, 404));
    }
}
