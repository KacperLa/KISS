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
    public static int appColumns(int availableWidthDp) { return availableWidthDp >= 600 ? 2 : 1; }
    public static int appColumns(int availableWidthDp, int override) {
        return override == 1 || override == 2 ? override : appColumns(availableWidthDp);
    }
    public static boolean isWideAppLayout(int widthPx, int heightPx) {
        return widthPx > 0 && heightPx > 0 && (double) widthPx / heightPx >= 0.75;
    }
    public static int columnsForLayout(boolean wide, int override) {
        return override == 1 || override == 2 ? override : (wide ? 2 : 1);
    }
    public static char initialLetter(String name) {
        if (name == null || name.isEmpty()) return 0;
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
        char initial = Character.toUpperCase(normalized.charAt(0));
        return initial >= 'A' && initial <= 'Z' ? initial : 0;
    }
    public static int favoriteCount(int widthDp) { return widthDp >= 600 ? 5 : 4; }
}
