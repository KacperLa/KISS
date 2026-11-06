package fr.neamar.kiss.dashboard;

import android.content.Intent;
import android.service.notification.StatusBarNotification;
import fr.neamar.kiss.notification.NotificationListener;

/** Live service reference only: no message contents are written to disk. */
public final class DashboardNotifications {
    public static final String CHANGED = "fr.neamar.kiss.DASHBOARD_NOTIFICATIONS_CHANGED";
    private static volatile NotificationListener listener;
    private DashboardNotifications() {}
    public static void connect(NotificationListener service) { listener = service; changed(service); }
    public static void disconnect(NotificationListener service) { listener = null; changed(service); }
    public static void changed(NotificationListener service) {
        service.sendBroadcast(new Intent(CHANGED).setPackage(service.getPackageName()));
    }
    public static StatusBarNotification[] active() {
        NotificationListener service = listener;
        if (service == null) return new StatusBarNotification[0];
        try {
            StatusBarNotification[] value = service.getActiveNotifications();
            return value == null ? new StatusBarNotification[0] : value;
        } catch (SecurityException | IllegalStateException e) { return new StatusBarNotification[0]; }
    }
    public static java.util.Map<String, Integer> ranks(StatusBarNotification[] notifications) {
        java.util.Map<String, Integer> ranks = new java.util.HashMap<>();
        NotificationListener service = listener;
        if (service == null) return ranks;
        try {
            android.service.notification.NotificationListenerService.RankingMap map = service.getCurrentRanking();
            if (map == null) return ranks;
            for (StatusBarNotification n : notifications) {
                android.service.notification.NotificationListenerService.Ranking ranking = new android.service.notification.NotificationListenerService.Ranking();
                if (map.getRanking(n.getKey(), ranking)) ranks.put(n.getKey(), ranking.getRank());
            }
        } catch (SecurityException | IllegalStateException ignored) { }
        return ranks;
    }
    public static void dismiss(String key) {
        NotificationListener service = listener;
        if (service != null) {
            try { service.cancelNotification(key); } catch (SecurityException | IllegalStateException ignored) { }
        }
    }
}
