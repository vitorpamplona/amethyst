package android.text.format;

import android.content.Context;
import java.util.Locale;

/**
 * JVM stand-in for android.text.format.Formatter.
 *
 * Only the file-size formatter is used, and it is arithmetic plus a unit
 * suffix — the same on any platform. Android has used SI units (1 kB = 1000 B)
 * since Oreo, so that is what this does; reporting binary units would show a
 * different number for the same file than the phone does.
 */
public final class Formatter {
    private Formatter() {}

    private static final String[] UNITS = {"B", "kB", "MB", "GB", "TB"};

    public static String formatFileSize(Context context, long bytes) {
        return format(bytes, 1000);
    }

    /** Android's "short" variant rounds harder; the same text is fine here. */
    public static String formatShortFileSize(Context context, long bytes) {
        return format(bytes, 1000);
    }

    private static String format(long bytes, int step) {
        double value = Math.abs(bytes);
        int unit = 0;
        while (value >= step && unit < UNITS.length - 1) {
            value /= step;
            unit++;
        }
        String sign = bytes < 0 ? "-" : "";
        // Whole bytes have no fraction on Android either.
        String number =
                unit == 0
                        ? String.format(Locale.getDefault(), "%.0f", value)
                        : String.format(Locale.getDefault(), value < 100 ? "%.2f" : "%.0f", value);
        return sign + number + " " + UNITS[unit];
    }
}
