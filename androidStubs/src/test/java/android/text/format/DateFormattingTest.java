package android.text.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * These produce text a user reads on every post, so they are implemented for
 * real rather than stubbed, and tested like real code.
 */
class DateFormattingTest {
    private final Locale original = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(original);
    }

    @Test
    void twentyFourHourIsInferredFromTheLocale() {
        Locale.setDefault(Locale.US);
        assertFalse(DateFormat.is24HourFormat(null), "en-US is a 12-hour locale");

        Locale.setDefault(Locale.GERMANY);
        assertTrue(DateFormat.is24HourFormat(null), "de-DE is a 24-hour locale");
    }

    @Test
    void bestPatternHonoursWhatWasAskedFor() {
        String dateAndTime = DateFormat.getBestDateTimePattern(Locale.US, "yMdHm");
        assertTrue(dateAndTime.contains("y") || dateAndTime.contains("M"), dateAndTime);

        String timeOnly = DateFormat.getBestDateTimePattern(Locale.US, "Hm");
        assertFalse(timeOnly.contains("y"), "a time-only skeleton must not produce a date pattern: " + timeOnly);
    }

    @Test
    void relativeTimeReadsAsPastOrFuture() {
        long now = 1_700_000_000_000L;
        assertEquals("just now", DateUtils.getRelativeTimeSpanString(now - 5_000L, now, DateUtils.MINUTE_IN_MILLIS));
        assertEquals("5 minutes ago", DateUtils.getRelativeTimeSpanString(now - 5 * DateUtils.MINUTE_IN_MILLIS, now, DateUtils.MINUTE_IN_MILLIS));
        assertEquals("1 hour ago", DateUtils.getRelativeTimeSpanString(now - DateUtils.HOUR_IN_MILLIS, now, DateUtils.MINUTE_IN_MILLIS));
        assertEquals("3 days ago", DateUtils.getRelativeTimeSpanString(now - 3 * DateUtils.DAY_IN_MILLIS, now, DateUtils.MINUTE_IN_MILLIS));
        assertEquals("in 2 hours", DateUtils.getRelativeTimeSpanString(now + 2 * DateUtils.HOUR_IN_MILLIS, now, DateUtils.MINUTE_IN_MILLIS));
    }

    @Test
    void singularAndPluralUnitsAgree() {
        long now = 1_700_000_000_000L;
        assertEquals("1 day ago", DateUtils.getRelativeTimeSpanString(now - DateUtils.DAY_IN_MILLIS, now, 0));
        assertEquals("2 days ago", DateUtils.getRelativeTimeSpanString(now - 2 * DateUtils.DAY_IN_MILLIS, now, 0));
    }

    @Test
    void theEnglishOnlyFallbackReportsItselfAsAGap() {
        java.util.List<String> gaps = new java.util.ArrayList<>();
        com.vitorpamplona.amethyst.stubs.PlatformGaps.setReporter((feature, detail) -> gaps.add(feature));
        try {
            DateUtils.getRelativeTimeSpanString(0L, 1L, 0);
            // Reported at most once per process, so accept either: what matters
            // is that the limitation is recorded somewhere, not silently shipped.
            assertTrue(
                    gaps.contains("DateUtils.getRelativeTimeSpanString")
                            || com.vitorpamplona.amethyst.stubs.PlatformGaps.seen().stream()
                                    .anyMatch(s -> s.startsWith("DateUtils.getRelativeTimeSpanString")),
                    "an English-only fallback must not ship silently");
        } finally {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.setReporter(null);
        }
    }
}
