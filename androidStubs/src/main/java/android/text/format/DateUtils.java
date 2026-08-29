package android.text.format;

import java.util.Locale;

/**
 * JVM stand-in for android.text.format.DateUtils.
 *
 * The relative-time text ("5 minutes ago") is user-visible on every post, so it
 * is implemented rather than stubbed. Amethyst has its own localized relative
 * formatter for most surfaces; this covers the call sites that still reach for
 * the framework one, and it is English-only — a real gap, reported as one, and
 * the reason to finish routing those call sites through the app's formatter.
 */
public final class DateUtils {
    public static final long SECOND_IN_MILLIS = 1000L;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    public static final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
    public static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    public static final long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;
    public static final long YEAR_IN_MILLIS = WEEK_IN_MILLIS * 52;

    public static final int FORMAT_SHOW_TIME = 0x00001;
    public static final int FORMAT_SHOW_DATE = 0x00010;
    public static final int FORMAT_ABBREV_RELATIVE = 0x40000;
    public static final int FORMAT_ABBREV_ALL = 0x80000;

    private DateUtils() {}

    public static CharSequence getRelativeTimeSpanString(long time) {
        return getRelativeTimeSpanString(time, System.currentTimeMillis(), MINUTE_IN_MILLIS);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
        return getRelativeTimeSpanString(time, now, minResolution, 0);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution, int flags) {
        if (!WARNED.getAndSet(true)) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "DateUtils.getRelativeTimeSpanString",
                    "the desktop fallback is English-only; route these call sites through the app's own localized formatter");
        }

        long deltaMs = Math.abs(now - time);
        boolean past = time <= now;

        long count;
        String unit;
        if (deltaMs < MINUTE_IN_MILLIS) {
            return past ? "just now" : "in a moment";
        } else if (deltaMs < HOUR_IN_MILLIS) {
            count = deltaMs / MINUTE_IN_MILLIS;
            unit = "minute";
        } else if (deltaMs < DAY_IN_MILLIS) {
            count = deltaMs / HOUR_IN_MILLIS;
            unit = "hour";
        } else if (deltaMs < WEEK_IN_MILLIS) {
            count = deltaMs / DAY_IN_MILLIS;
            unit = "day";
        } else if (deltaMs < YEAR_IN_MILLIS) {
            count = deltaMs / WEEK_IN_MILLIS;
            unit = "week";
        } else {
            count = deltaMs / YEAR_IN_MILLIS;
            unit = "year";
        }

        String plural = count == 1 ? unit : unit + "s";
        return past
                ? String.format(Locale.ROOT, "%d %s ago", count, plural)
                : String.format(Locale.ROOT, "in %d %s", count, plural);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
}
