package androidx.appcompat.app;

import androidx.core.os.LocaleListCompat;

/**
 * JVM stand-in for AppCompatDelegate.
 *
 * The app uses this to read and set the in-app language. That is a real user
 * setting, so it is kept as real state here rather than dropped; the desktop
 * shell reads it back to pick the locale for AndroidResourceTable.
 */
public final class AppCompatDelegate {
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;
    public static final int MODE_NIGHT_FOLLOW_SYSTEM = -1;

    private static volatile LocaleListCompat applicationLocales = LocaleListCompat.getEmptyLocaleList();
    private static volatile int defaultNightMode = MODE_NIGHT_FOLLOW_SYSTEM;

    private AppCompatDelegate() {}

    public static LocaleListCompat getApplicationLocales() { return applicationLocales; }

    public static void setApplicationLocales(LocaleListCompat locales) {
        applicationLocales = locales == null ? LocaleListCompat.getEmptyLocaleList() : locales;
    }

    public static int getDefaultNightMode() { return defaultNightMode; }

    public static void setDefaultNightMode(int mode) { defaultNightMode = mode; }
}
