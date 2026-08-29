package android.app;

import java.util.ArrayList;
import java.util.List;

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

    public void notify(int id, Notification notification) { notify(null, id, notification); }

    public void notify(String tag, int id, Notification notification) {
        Presenter p = presenter;
        if (p != null) p.notify(tag, id, notification);
    }

    public void cancel(int id) { cancel(null, id); }

    public void cancel(String tag, int id) {
        Presenter p = presenter;
        if (p != null) p.cancel(tag, id);
    }

    public void cancelAll() {}

    public boolean areNotificationsEnabled() { return true; }
}
