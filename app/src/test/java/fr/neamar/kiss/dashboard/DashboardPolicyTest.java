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
    @Test void columnOverridePersistsUserChoiceWithoutChangingAuto() {
        assertEquals(2, DashboardPolicy.appColumns(400, 2));
        assertEquals(1, DashboardPolicy.appColumns(900, 1));
        assertEquals(1, DashboardPolicy.appColumns(400, 0));
        assertEquals(2, DashboardPolicy.appColumns(900, 0));
        assertEquals(1, DashboardPolicy.appColumns(400, 99));
    }
    @Test void windowShapeAdaptsIndependentlyOfDisplayScaling() {
        assertTrue(DashboardPolicy.isWideAppLayout(2076, 2152));
        assertTrue(DashboardPolicy.isWideAppLayout(1038, 1076));
        assertFalse(DashboardPolicy.isWideAppLayout(1080, 2424));
        assertFalse(DashboardPolicy.isWideAppLayout(540, 1212));
        assertTrue(DashboardPolicy.isWideAppLayout(2424, 1080));
        assertFalse(DashboardPolicy.isWideAppLayout(0, 0));
        assertEquals(2, DashboardPolicy.columnsForLayout(true, 0));
        assertEquals(1, DashboardPolicy.columnsForLayout(false, 0));
        assertEquals(1, DashboardPolicy.columnsForLayout(true, 1));
        assertEquals(2, DashboardPolicy.columnsForLayout(false, 2));
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
