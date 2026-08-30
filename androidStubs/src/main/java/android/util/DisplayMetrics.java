package android.util;

import android.content.res.Configuration;

/**
 * JVM stand-in for android.util.DisplayMetrics.
 *
 * Derived from the configuration the JVM already fills from the real screen,
 * so a bitmap sized by {@link #density} comes out the size it should be. On
 * Android {@code density} is dpi/160; the same relation holds here.
 */
public class DisplayMetrics {
    public static final int DENSITY_DEFAULT = 160;

    public int widthPixels;
    public int heightPixels;
    public int densityDpi = DENSITY_DEFAULT;
    public float density = 1.0f;
    public float scaledDensity = 1.0f;
    public float xdpi = DENSITY_DEFAULT;
    public float ydpi = DENSITY_DEFAULT;

    public static DisplayMetrics fromConfiguration(Configuration configuration) {
        DisplayMetrics metrics = new DisplayMetrics();
        if (configuration == null) return metrics;
        metrics.densityDpi = configuration.densityDpi;
        metrics.density = configuration.densityDpi / (float) DENSITY_DEFAULT;
        metrics.scaledDensity = metrics.density;
        metrics.xdpi = configuration.densityDpi;
        metrics.ydpi = configuration.densityDpi;
        metrics.widthPixels = Math.round(configuration.screenWidthDp * metrics.density);
        metrics.heightPixels = Math.round(configuration.screenHeightDp * metrics.density);
        return metrics;
    }
}
