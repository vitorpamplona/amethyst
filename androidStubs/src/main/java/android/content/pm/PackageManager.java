package android.content.pm;

import android.content.Intent;
import java.util.Collections;
import java.util.List;

/**
 * JVM stand-in for android.content.pm.PackageManager.
 *
 * Its use here is asking "is there an app that can handle this?" — a question
 * that has no desktop equivalent, because desktop dispatch goes through the OS
 * rather than an app registry. Every query answers "nothing found", which sends
 * callers down the fallback path they already have for a phone with no matching
 * app installed.
 */
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int GET_META_DATA = 0x00000080;
    public static final int MATCH_DEFAULT_ONLY = 0x00010000;

    public static final PackageManager EMPTY = new PackageManager();

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        return Collections.emptyList();
    }

    public ResolveInfo resolveActivity(Intent intent, int flags) { return null; }

    public Intent getLaunchIntentForPackage(String packageName) { return null; }

    public boolean hasSystemFeature(String name) { return false; }

    public int checkPermission(String permission, String packageName) { return PERMISSION_DENIED; }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() {}

        public NameNotFoundException(String message) { super(message); }
    }
}
