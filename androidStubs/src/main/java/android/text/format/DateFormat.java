package android.text.format;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * JVM stand-in for android.text.format.DateFormat.
 *
 * Implemented for real rather than stubbed: every one of these produces text
 * a user reads, and the JDK's date formatting is the same CLDR data Android's
 * is built on, so the results agree. Returning a placeholder would put wrong
 * timestamps on every post.
 */
public final class DateFormat {
    private DateFormat() {}

    /**
     * Android reads a per-user 12/24-hour setting. Desktop has no single
     * equivalent, so this infers it from the locale's own short time pattern —
     * which is what the setting defaults to on Android anyway.
     */
    public static boolean is24HourFormat(Context context) {
        String pattern = localeTimePattern(Locale.getDefault());
        return !pattern.contains("a");
    }

    public static java.text.DateFormat getTimeFormat(Context context) {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, Locale.getDefault());
    }

    public static java.text.DateFormat getDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.getDefault());
    }

    public static java.text.DateFormat getMediumDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault());
    }

    public static java.text.DateFormat getLongDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault());
    }

    /**
     * Android picks the closest pattern the locale has for the requested
     * fields. The JDK exposes no such chooser, so this returns the locale's own
     * short date-time pattern — right for the common "date plus time" ask, and
     * closer than echoing the skeleton back.
     */
    public static String getBestDateTimePattern(Locale locale, String skeleton) {
        boolean wantsDate = skeleton.indexOf('y') >= 0 || skeleton.indexOf('M') >= 0 || skeleton.indexOf('d') >= 0;
        boolean wantsTime = skeleton.indexOf('H') >= 0 || skeleton.indexOf('h') >= 0 || skeleton.indexOf('m') >= 0;

        if (wantsDate && wantsTime) return localePattern(locale, FormatStyle.SHORT, FormatStyle.SHORT);
        if (wantsTime) return localeTimePattern(locale);
        return localePattern(locale, FormatStyle.SHORT, null);
    }

    private static String localeTimePattern(Locale locale) {
        return localePattern(locale, null, FormatStyle.SHORT);
    }

    private static String localePattern(Locale locale, FormatStyle date, FormatStyle time) {
        DateTimeFormatter formatter =
                (date != null && time != null)
                        ? DateTimeFormatter.ofLocalizedDateTime(date, time)
                        : (date != null)
                                ? DateTimeFormatter.ofLocalizedDate(date)
                                : DateTimeFormatter.ofLocalizedTime(time);
        java.text.DateFormat fallback =
                (date != null && time != null)
                        ? java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, locale)
                        : (date != null)
                                ? java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale)
                                : java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale);
        return (fallback instanceof SimpleDateFormat) ? ((SimpleDateFormat) fallback).toPattern() : formatter.toString();
    }
}
