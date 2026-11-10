package fr.neamar.kiss.dashboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlphabetNavigationPolicyTest {
    @Test void sparseGroupsNeverSelectAnEmptyLetterInEitherDirection() {
        String letters = "CMY";
        for (int y = -20; y <= 280; y++) assertTrue(letters.indexOf(AlphabetNavigationPolicy.at(letters, y, 260)) >= 0);
        assertEquals('C', AlphabetNavigationPolicy.at(letters, 0, 260));
        assertEquals('M', AlphabetNavigationPolicy.at(letters, 130, 260));
        assertEquals('Y', AlphabetNavigationPolicy.at(letters, 260, 260));
        assertEquals('C', AlphabetNavigationPolicy.at(letters, 0, 260));
    }

    @Test void everyPopulatedAnchorSelectsItsOwnLetter() {
        for (String letters : new String[]{"ABCDEFGHIJKLMNOPQRSTUVWXYZ", "ABFGST", "CMY", "Q"}) {
            for (char letter : letters.toCharArray()) {
                assertEquals(letter, AlphabetNavigationPolicy.at(letters, AlphabetNavigationPolicy.position(letters, letter, 341), 341));
            }
        }
    }

    @Test void accessibilityStepsSkipGapsAndClampAtEnds() {
        assertEquals('C', AlphabetNavigationPolicy.step("CMY", (char) 0, true));
        assertEquals('Y', AlphabetNavigationPolicy.step("CMY", (char) 0, false));
        assertEquals('M', AlphabetNavigationPolicy.step("CMY", 'C', true));
        assertEquals('Y', AlphabetNavigationPolicy.step("CMY", 'M', true));
        assertEquals('Y', AlphabetNavigationPolicy.step("CMY", 'Y', true));
        assertEquals('M', AlphabetNavigationPolicy.step("CMY", 'Y', false));
        assertEquals('C', AlphabetNavigationPolicy.step("CMY", 'M', false));
        assertEquals('C', AlphabetNavigationPolicy.step("CMY", 'C', false));
    }

    @Test void emptySingleAndChangedGroupsAreSafe() {
        assertEquals(0, AlphabetNavigationPolicy.at("", 140, 260));
        assertEquals(0, AlphabetNavigationPolicy.step("", 'A', true));
        assertEquals('Q', AlphabetNavigationPolicy.at("Q", 999, 260));
        assertEquals('Q', AlphabetNavigationPolicy.step("Q", 'Q', false));
        assertEquals('Q', AlphabetNavigationPolicy.step("Q", 'Q', true));
        assertEquals('M', AlphabetNavigationPolicy.step("MY", 'C', true));
        assertEquals('C', AlphabetNavigationPolicy.at("CMY", 100, 0));
        assertEquals(0, AlphabetNavigationPolicy.position("", 'A', 260));
    }
}
