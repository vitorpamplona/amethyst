package android.content.pm;

/** JVM stand-in for android.content.pm.ResolveInfo. See PackageManager. */
public class ResolveInfo {
    public ActivityInfo activityInfo;

    public CharSequence loadLabel(PackageManager packageManager) { return ""; }
}
