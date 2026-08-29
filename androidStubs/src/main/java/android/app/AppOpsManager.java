package android.app;

/**
 * JVM stand-in for android.app.AppOpsManager.
 *
 * App ops are Android's per-app permission ledger; a desktop has no such thing
 * to consult. The single use here asks whether this app may enter
 * picture-in-picture, which is declared as having no desktop counterpart, so
 * the honest answer is the same one Android gives a user who turned it off —
 * MODE_IGNORED — and the caller's existing "hide the button" path runs.
 */
public class AppOpsManager {
    public static final int MODE_ALLOWED = 0;
    public static final int MODE_IGNORED = 1;
    public static final int MODE_ERRORED = 2;
    public static final int MODE_DEFAULT = 3;

    public static final String OPSTR_PICTURE_IN_PICTURE = "android:picture_in_picture";

    public int checkOpNoThrow(String op, int uid, String packageName) { return MODE_IGNORED; }

    public int unsafeCheckOpNoThrow(String op, int uid, String packageName) { return MODE_IGNORED; }

    public int checkOp(String op, int uid, String packageName) { return MODE_IGNORED; }
}
