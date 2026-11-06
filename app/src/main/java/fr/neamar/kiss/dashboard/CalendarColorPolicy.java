package fr.neamar.kiss.dashboard;

/** Preserve provider display color, falling back only when it is absent. */
public final class CalendarColorPolicy {
    private CalendarColorPolicy() {}
    public static int resolve(Integer displayColor, Integer calendarColor, int fallback) {
        if (displayColor != null) return displayColor;
        if (calendarColor != null) return calendarColor;
        return fallback;
    }
}

