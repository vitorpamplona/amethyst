package android.content;

import android.content.res.Resources;
import java.io.File;

/**
 * JVM stand-in for android.content.Context.
 *
 * Declares only the surface shared Amethyst code actually calls, so an
 * unsupported call fails at compile time on the JVM target rather than at
 * runtime. Behaviour lives in the JVM implementation
 * (com.vitorpamplona.amethyst.shared.platform.JvmContext), not here.
 */
public abstract class Context {
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String AUDIO_SERVICE = "audio";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String POWER_SERVICE = "power";
    public static final String LOCATION_SERVICE = "location";
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String WINDOW_SERVICE = "window";

    public static final int MODE_PRIVATE = 0;

    public abstract String getPackageName();

    public abstract Resources getResources();

    public abstract String getString(int resId);

    public abstract String getString(int resId, Object... formatArgs);

    /** On the JVM there is one process-wide context, so this returns itself. */
    public Context getApplicationContext() {
        return this;
    }

    public abstract File getCacheDir();

    public abstract File getFilesDir();

    public abstract File getExternalCacheDir();

    public abstract File getExternalFilesDir(String type);

    public abstract SharedPreferences getSharedPreferences(String name, int mode);

    public abstract ContentResolver getContentResolver();

    /**
     * Returns null for every service the desktop does not model. Callers must
     * already handle null — Android itself returns null for a service missing
     * on a given device — so this degrades along a path that is already tested
     * rather than inventing a new failure mode.
     */
    public Object getSystemService(String name) {
        return null;
    }

    public <T> T getSystemService(Class<T> serviceClass) {
        return null;
    }

    /** Inert: activity dispatch is a platform concern handled at the call site. */
    public void startActivity(Intent intent) {}

    public void startActivity(Intent intent, android.os.Bundle options) {}

    public void sendBroadcast(Intent intent) {}

    public int checkSelfPermission(String permission) {
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }
}
