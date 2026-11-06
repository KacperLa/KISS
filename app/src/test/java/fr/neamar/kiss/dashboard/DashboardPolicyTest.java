package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DashboardPolicyTest {
    @Test void pullOnlyAtTopAndDownward() {
        assertTrue(DashboardPolicy.opensSearch(true, 10, 150, 100));
        assertFalse(DashboardPolicy.opensSearch(false, 10, 150, 100));
        assertFalse(DashboardPolicy.opensSearch(true, 10, -150, 100));
        assertFalse(DashboardPolicy.opensSearch(true, 200, 150, 100));
        assertFalse(DashboardPolicy.opensSearch(true, 0, 99, 100));
    }
    @Test void alphabetClampsEdges() {
        assertEquals(0, DashboardPolicy.letterIndex(-1, 260));
        assertEquals(12, DashboardPolicy.letterIndex(125, 260));
        assertEquals(25, DashboardPolicy.letterIndex(280, 260));
        assertEquals(0, DashboardPolicy.letterIndex(10, 0));
    }
    @Test void appGridAdaptsWithoutShrinkingText() {
        assertEquals(1, DashboardPolicy.appColumns(359));
        assertEquals(1, DashboardPolicy.appColumns(599));
        assertEquals(2, DashboardPolicy.appColumns(600));
        assertEquals(2, DashboardPolicy.appColumns(900));
    }
    @Test void indexNormalizesLatinInitialsWithoutHidingOtherNames() {
        assertEquals('M', DashboardPolicy.initialLetter("Messages"));
        assertEquals('E', DashboardPolicy.initialLetter("École"));
        assertEquals('A', DashboardPolicy.initialLetter("alarm"));
        assertEquals(0, DashboardPolicy.initialLetter("123 Notes"));
        assertEquals(0, DashboardPolicy.initialLetter(""));
        assertEquals(0, DashboardPolicy.initialLetter(null));
    }
    @Test void dockAdaptsToWidth() {
        assertEquals(4, DashboardPolicy.favoriteCount(400));
        assertEquals(5, DashboardPolicy.favoriteCount(800));
    }
}
