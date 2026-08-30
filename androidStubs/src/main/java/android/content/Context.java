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
    public static final String ALARM_SERVICE = "alarm";
    public static final String APP_OPS_SERVICE = "appops";
    public static final String UI_MODE_SERVICE = "uimode";

    public static final int MODE_PRIVATE = 0;
    public static final int MODE_APPEND = 0x8000;

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
     * Desktop has no package manager. Returning a stub rather than null keeps
     * the common "can anything handle this intent" checks compiling; each
     * answers no, which routes callers to their fallback path.
     */
    public android.content.pm.PackageManager getPackageManager() {
        return android.content.pm.PackageManager.EMPTY;
    }

    /**
     * Returns null for every service the desktop does not model. Callers must
     * already handle null — Android itself returns null for a service missing
     * on a given device — so this degrades along a path that is already tested
     * rather than inventing a new failure mode.
     */
    /**
     * The app's private files live under the JVM's per-user app data directory,
     * which is what {@link #getFilesDir} already resolves. Real file I/O, so a
     * crash report written before a restart is still there to read after it.
     */
    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException {
        java.io.File dir = getFilesDir();
        if (dir != null) dir.mkdirs();
        return new java.io.FileOutputStream(new java.io.File(dir, name), (mode & MODE_APPEND) != 0);
    }

    public java.io.FileInputStream openFileInput(String name) throws java.io.FileNotFoundException {
        return new java.io.FileInputStream(new java.io.File(getFilesDir(), name));
    }

    public boolean deleteFile(String name) {
        return new java.io.File(getFilesDir(), name).delete();
    }

    public java.io.File getFileStreamPath(String name) {
        return new java.io.File(getFilesDir(), name);
    }

    public String[] fileList() {
        java.io.File dir = getFilesDir();
        String[] names = dir == null ? null : dir.list();
        return names == null ? new String[0] : names;
    }

    /**
     * Hands back the services this platform actually models, and null for the
     * rest. Null is the right answer for those — Android itself returns null
     * for a service missing on a given device, so callers already handle it —
     * but null for a service that *is* modelled would be its own bug: code
     * asking ActivityManager for the memory class would size its caches for
     * zero bytes rather than for this machine's heap.
     */
    public Object getSystemService(String name) {
        if (name == null) return null;
        switch (name) {
            case ACTIVITY_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.app.ActivityManager());
            case CONNECTIVITY_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.net.ConnectivityManager());
            case NOTIFICATION_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.app.NotificationManager());
            case AUDIO_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.media.AudioManager());
            case ALARM_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.app.AlarmManager());
            case APP_OPS_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.app.AppOpsManager());
            case UI_MODE_SERVICE:
                return SERVICES.computeIfAbsent(name, key -> new android.app.UiModeManager());
            default:
                return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> serviceClass) {
        if (serviceClass == null) return null;
        Object service = getSystemService(nameOf(serviceClass));
        return serviceClass.isInstance(service) ? (T) service : null;
    }

    private static String nameOf(Class<?> serviceClass) {
        if (serviceClass == android.app.ActivityManager.class) return ACTIVITY_SERVICE;
        if (serviceClass == android.net.ConnectivityManager.class) return CONNECTIVITY_SERVICE;
        if (serviceClass == android.app.NotificationManager.class) return NOTIFICATION_SERVICE;
        if (serviceClass == android.media.AudioManager.class) return AUDIO_SERVICE;
        if (serviceClass == android.app.AlarmManager.class) return ALARM_SERVICE;
        if (serviceClass == android.app.AppOpsManager.class) return APP_OPS_SERVICE;
        if (serviceClass == android.app.UiModeManager.class) return UI_MODE_SERVICE;
        return null;
    }

    /** One instance per service, as on Android, shared across every Context. */
    private static final java.util.Map<String, Object> SERVICES = new java.util.concurrent.ConcurrentHashMap<>();

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

    /**
     * Desktop has no service manager. {@code ForegroundService} is declared as
     * having no desktop counterpart — the work these services do belongs to
     * long-lived objects the app owns — so a start request is recorded against
     * that declaration rather than silently doing nothing.
     */
    public ComponentName startService(Intent intent) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Context.startService",
                "desktop has no service manager; " + intent.getComponentClassName()
                        + " must be owned directly by the app");
        return null;
    }

    public ComponentName startForegroundService(Intent intent) {
        return startService(intent);
    }

    public boolean stopService(Intent intent) {
        return false;
    }

    public void sendBroadcast(Intent intent) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Context.sendBroadcast", "desktop has no broadcast bus; action=" + intent.getAction());
    }

    public int checkSelfPermission(String permission) {
        return android.content.pm.PackageManager.PERMISSION_DENIED;
    }
}
