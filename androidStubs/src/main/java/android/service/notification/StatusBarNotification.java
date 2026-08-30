package android.service.notification;

import android.app.Notification;

/**
 * JVM stand-in for android.service.notification.StatusBarNotification — one
 * notification as it currently stands in the shade.
 */
public class StatusBarNotification {
    private final String tag;
    private final int id;
    private final Notification notification;

    public StatusBarNotification(String tag, int id, Notification notification) {
        this.tag = tag;
        this.id = id;
        this.notification = notification;
    }

    public String getTag() { return tag; }

    public int getId() { return id; }

    public Notification getNotification() { return notification; }

    public String getGroupKey() { return notification == null ? null : notification.group; }

    public String getKey() { return tag + "|" + id; }
}
