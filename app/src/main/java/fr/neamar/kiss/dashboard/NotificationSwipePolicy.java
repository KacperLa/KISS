package fr.neamar.kiss.dashboard;
public final class NotificationSwipePolicy {
    private NotificationSwipePolicy() {}
    public static boolean shouldDismiss(float drag, int width, int minimum) {
        return Math.abs(drag) >= Math.max(minimum, width * 0.25f);
    }
}
