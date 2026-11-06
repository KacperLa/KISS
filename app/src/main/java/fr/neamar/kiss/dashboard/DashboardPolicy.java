package fr.neamar.kiss.dashboard;

/** Pure layout/gesture rules kept testable without Android. */
public final class DashboardPolicy {
    private DashboardPolicy() {}
    public static boolean opensSearch(boolean startedAtTop, float dx, float dy, float threshold) {
        return startedAtTop && dy > threshold && dy > Math.abs(dx) * 1.5f;
    }
    public static int letterIndex(float y, float height) {
        if (height <= 0) return 0;
        return Math.max(0, Math.min(25, (int) (y / height * 26)));
    }
    public static int favoriteCount(int widthDp) { return widthDp >= 600 ? 5 : 4; }
}
