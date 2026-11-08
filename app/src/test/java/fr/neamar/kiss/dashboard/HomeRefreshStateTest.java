package fr.neamar.kiss.dashboard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomeRefreshStateTest {
    @Test void scrollingOrRailNavigationDoesNotRequestARebuild() {
        HomeRefreshState state = new HomeRefreshState();
        state.rendered();
        state.navigationStarted();
        state.navigationStarted();
        assertFalse(state.hasPendingRefresh());
    }

    @Test void realDataChangesSurviveGesturesAndCoalesceUntilRendered() {
        HomeRefreshState state = new HomeRefreshState();
        state.requestRefresh();
        state.navigationStarted();
        state.requestRefresh();
        state.navigationStarted();
        assertTrue(state.hasPendingRefresh());
        state.rendered();
        assertFalse(state.hasPendingRefresh());
    }

    @Test void oldRestorationCannotOverrideNewNavigation() {
        HomeRefreshState state = new HomeRefreshState();
        state.rendered();
        long saved = state.generation();
        assertTrue(state.canRestore(saved));
        state.navigationStarted();
        assertFalse(state.canRestore(saved));
    }

    @Test void newRenderInvalidatesAnOlderLayoutCallback() {
        HomeRefreshState state = new HomeRefreshState();
        state.rendered();
        long saved = state.generation();
        state.rendered();
        assertFalse(state.canRestore(saved));
        assertTrue(state.canRestore(state.generation()));
    }

    @Test void pendingDataDoesNotInvalidateTheCurrentLayoutRestoration() {
        HomeRefreshState state = new HomeRefreshState();
        state.rendered();
        long saved = state.generation();
        state.requestRefresh();
        assertTrue(state.canRestore(saved));
        assertTrue(state.hasPendingRefresh());
    }
}
