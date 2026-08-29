package android.app;

/** JVM stand-in for android.app.NotificationChannelGroup. */
public class NotificationChannelGroup {
    private final String id;
    private final CharSequence name;

    public NotificationChannelGroup(String id, CharSequence name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }

    public CharSequence getName() { return name; }
}
