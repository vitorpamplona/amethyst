package android.app;

/** JVM stand-in for android.app.Notification. */
public class Notification {
    public static final int PRIORITY_LOW = -1;
    public static final int PRIORITY_DEFAULT = 0;
    public static final int PRIORITY_HIGH = 1;
    public static final int DEFAULT_ALL = -1;
    public static final int FLAG_ONGOING_EVENT = 0x00000002;

    public int flags;
    public int priority;
    public String category;
}
