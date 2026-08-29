package android.content.pm;

/** JVM stand-in for android.content.pm.ApplicationInfo. Pure data. */
public class ApplicationInfo {
    public static final int FLAG_LARGE_HEAP = 1 << 20;
    public static final int FLAG_DEBUGGABLE = 1 << 1;
    public static final int FLAG_SYSTEM = 1;

    public String packageName;
    public String name;
    public int flags;
    public int targetSdkVersion = android.os.Build.VERSION.SDK_INT;
    public String dataDir = System.getProperty("user.home", ".");
}
