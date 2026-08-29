package android.provider;

/**
 * JVM stand-in for android.provider.Settings.
 *
 * The action strings exist so intent-building code compiles; firing one lands
 * in IntentDispatcher, which reports it as a gap because desktop has no
 * settings screens to deep-link into.
 */
public final class Settings {
    private Settings() {}

    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";
    public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS =
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
    public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    public static final String ACTION_LOCALE_SETTINGS = "android.settings.LOCALE_SETTINGS";
    public static final String ACTION_APP_LOCALE_SETTINGS = "android.settings.APP_LOCALE_SETTINGS";
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";
    public static final String EXTRA_CHANNEL_ID = "android.provider.extra.CHANNEL_ID";

    public static final class Secure {
        private Secure() {}

        public static String getString(Object resolver, String name) { return null; }
    }

    public static final class Global {
        private Global() {}

        public static String getString(Object resolver, String name) { return null; }
    }
}
