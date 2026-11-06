package fr.neamar.kiss.dashboard;
/** Map one continuous rail coordinate to three sections followed by A-Z. */
public final class RailNavigationPolicy {
    private RailNavigationPolicy() {}
    public static int index(float y, int notificationsBottom, int tasksBottom, int alphabetTop, int alphabetBottom) {
        return index(y, notificationsBottom, tasksBottom, alphabetTop, alphabetBottom, 0);
    }
    public static int index(float y, int notificationsBottom, int tasksBottom, int alphabetTop, int alphabetBottom, int labelInset) {
        if (y < notificationsBottom) return 0;
        if (y < tasksBottom) return 1;
        if (y < alphabetTop) return 2;
        return 3 + DashboardPolicy.letterIndex(y - alphabetTop - labelInset, Math.max(1, alphabetBottom - alphabetTop - 2 * labelInset));
    }
}
