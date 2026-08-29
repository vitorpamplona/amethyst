package android.content.pm;

/** JVM stand-in for android.content.pm.ActivityInfo. See PackageManager. */
public class ActivityInfo {
    public String packageName;
    public String name;

    public static final int SCREEN_ORIENTATION_PORTRAIT = 1;
    public static final int SCREEN_ORIENTATION_LANDSCAPE = 0;
    public static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
}
