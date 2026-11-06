package fr.neamar.kiss.dashboard;

/** Notification launches are user gestures, never background jobs. */
public final class NotificationLaunchPolicy {
    private NotificationLaunchPolicy() {}
    public static boolean canSend(boolean resumed, boolean destroyed, boolean finishing) {
        return resumed && !destroyed && !finishing;
    }
}
