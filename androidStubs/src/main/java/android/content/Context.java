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
    /**
     * The process-wide context, installed by the JVM app at startup.
     *
     * Android hands every component a Context; on the JVM there is one per
     * process, so Application, Service and Activity all resolve through this
     * rather than each carrying their own.
     */
    private static volatile Context applicationContext;

    public static void installApplicationContext(Context context) {
        applicationContext = context;
    }

    /**
     * Fails loudly rather than returning null. A null Context surfaces far from
     * the missing install as an NPE inside unrelated code; this names the cause.
     */
    protected static Context requireApplicationContext() {
        Context context = applicationContext;
        if (context == null) {
            throw new IllegalStateException(
                    "No application Context installed. The JVM app must call "
                            + "Context.installApplicationContext(...) during startup.");
        }
        return context;
    }

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

    /** On the JVM there is one process-wide context. */
    public Context getApplicationContext() {
        Context context = applicationContext;
        return context == null ? this : context;
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

    /**
     * Carried out by {@link IntentDispatcher}: opening a link and sharing text
     * both work on desktop, and anything it cannot do is reported as a platform
     * gap rather than silently dropped.
     */
    public void startActivity(Intent intent) {
        IntentDispatcher.dispatch(intent);
    }

    public void startActivity(Intent intent, android.os.Bundle options) {
        IntentDispatcher.dispatch(intent);
    }

    public void sendBroadcast(Intent intent) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Context.sendBroadcast", "desktop has no broadcast bus; action=" + intent.getAction());
    }

    public int checkSelfPermission(String permission) {
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }
}
