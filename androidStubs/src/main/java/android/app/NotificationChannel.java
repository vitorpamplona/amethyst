package android.app;

/** JVM stand-in for android.app.NotificationChannel. */
public class NotificationChannel {
    private final String id;
    private final CharSequence name;
    private final int importance;
    private String description;
    private String group;

    public NotificationChannel(String id, CharSequence name, int importance) {
        this.id = id;
        this.name = name;
        this.importance = importance;
    }

    public String getId() { return id; }

    public CharSequence getName() { return name; }

    public int getImportance() { return importance; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getGroup() { return group; }

    public void setGroup(String group) { this.group = group; }

    public void setShowBadge(boolean showBadge) {}

    public void enableVibration(boolean enable) {}

    public void enableLights(boolean enable) {}
}
