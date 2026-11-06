package fr.neamar.kiss.dashboard;
/** Map one continuous rail coordinate to three sections followed by A-Z. */
public final class RailNavigationPolicy {
    private RailNavigationPolicy() {}
    public static int index(float y, int notificationsBottom, int tasksBottom, int alphabetTop, int alphabetBottom) {
        if (y < notificationsBottom) return 0;
        if (y < tasksBottom) return 1;
        if (y < alphabetTop) return 2;
        return 3 + DashboardPolicy.letterIndex(y - alphabetTop, alphabetBottom - alphabetTop);
    }
}
