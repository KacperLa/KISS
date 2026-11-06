package fr.neamar.kiss.dashboard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RailScrollMappingTest {
    @Test void terminalAnchorReachesRowsBelowLastHeading() {
        float[] feed = {0, 1000, 1800};
        float[] rail = {24, 350, 374};
        assertEquals(1400f, RailScrollMapping.interpolate(362, rail, feed));
        assertEquals(1800f, RailScrollMapping.interpolate(374, rail, feed));
    }
    @Test void followsSemanticAnchorsAndInterpolatesBetweenSections() {
        float[] feed = {0, 1000, 1200, 2000, 2600};
        float[] rail = {24, 72, 120, 170, 400};
        assertEquals(72f, RailScrollMapping.interpolate(1000, feed, rail));
        assertEquals(48f, RailScrollMapping.interpolate(500, feed, rail));
        assertEquals(96f, RailScrollMapping.interpolate(1100, feed, rail));
        assertEquals(1100f, RailScrollMapping.interpolate(96, rail, feed));
        assertEquals(24f, RailScrollMapping.interpolate(-10, feed, rail));
        assertEquals(400f, RailScrollMapping.interpolate(9000, feed, rail));
        assertEquals(0f, RailScrollMapping.interpolate(1, new float[0], new float[0]));
    }
}
