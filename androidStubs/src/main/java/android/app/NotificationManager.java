package android.app;

import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JVM stand-in for android.app.NotificationManager.
 *
 * Channels are tracked (code reads them back) but posting routes to a delegate
 * the desktop app installs — :desktopApp already bundles per-OS notification
 * bridges. With no delegate installed, posting is a no-op rather than a crash.
 */
public class NotificationManager {
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;
    public static final int IMPORTANCE_MAX = 5;
    public static final int IMPORTANCE_UNSPECIFIED = -1000;

    public interface Presenter {
        void notify(String tag, int id, Notification notification);

        void cancel(String tag, int id);
    }

    private static volatile Presenter presenter;

    public static void setPresenter(Presenter value) { presenter = value; }

    private final List<NotificationChannel> channels = new ArrayList<>();
    private final List<NotificationChannelGroup> groups = new ArrayList<>();

    public void createNotificationChannel(NotificationChannel channel) { channels.add(channel); }

    public void createNotificationChannelGroup(NotificationChannelGroup group) { groups.add(group); }

    public List<NotificationChannel> getNotificationChannels() { return channels; }

    public List<NotificationChannelGroup> getNotificationChannelGroups() { return groups; }

    public NotificationChannel getNotificationChannel(String channelId) {
        for (NotificationChannel c : channels) {
            if (c.getId().equals(channelId)) return c;
        }
        return null;
    }

    public void deleteNotificationChannel(String channelId) {
        channels.removeIf(c -> c.getId().equals(channelId));
    }

    /**
     * What has been posted and not yet cancelled.
     *
     * Tracked here rather than left to the presenter because the app *reads*
     * it: the group-summary cleanup walks this list to decide which summaries
     * are now childless. An empty list would make it silently never clean one
     * up, and stale summaries would pile up in the shade.
     */
    private static final Map<String, StatusBarNotification> ACTIVE = new LinkedHashMap<>();

    public void notify(int id, Notification notification) { notify(null, id, notification); }

    public void notify(String tag, int id, Notification notification) {
        synchronized (ACTIVE) {
            ACTIVE.put(keyOf(tag, id), new StatusBarNotification(tag, id, notification));
        }
        Presenter p = presenter;
        if (p != null) p.notify(tag, id, notification);
    }

    public void cancel(int id) { cancel(null, id); }

    public void cancel(String tag, int id) {
        synchronized (ACTIVE) {
            ACTIVE.remove(keyOf(tag, id));
        }
        Presenter p = presenter;
        if (p != null) p.cancel(tag, id);
    }

    public void cancelAll() {
        List<StatusBarNotification> posted;
        synchronized (ACTIVE) {
            posted = new ArrayList<>(ACTIVE.values());
            ACTIVE.clear();
        }
        Presenter p = presenter;
        if (p != null) {
            for (StatusBarNotification each : posted) p.cancel(each.getTag(), each.getId());
        }
    }

    public StatusBarNotification[] getActiveNotifications() {
        synchronized (ACTIVE) {
            return ACTIVE.values().toArray(new StatusBarNotification[0]);
        }
    }

    private static String keyOf(String tag, int id) { return tag + "|" + id; }

    public boolean areNotificationsEnabled() { return true; }
}
