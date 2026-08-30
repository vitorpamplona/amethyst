package android.icu.text;

import android.icu.util.ULocale;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * JVM stand-in for android.icu.text.LocaleData.
 *
 * Implemented rather than stubbed, and it is a small implementation: the app
 * asks ICU exactly one question — does this locale measure in miles or in
 * kilometres — and CLDR's answer to it is a three-line table. The US system
 * covers the United States, Liberia and Myanmar; the UK system covers the
 * United Kingdom (miles on the road, metric elsewhere); everywhere else is SI.
 *
 * That is the same data ICU would return, so a workout published from the
 * desktop carries the same units the Android build would give it. Faking this
 * as "always SI" would quietly publish kilometres to a user who runs in miles.
 */
public final class LocaleData {
    private LocaleData() {}

    public enum MeasurementSystem {
        SI,
        US,
        UK,
    }

    /** CLDR's `measurementSystem` territory data, which is this short. */
    private static final List<String> US_SYSTEM = Arrays.asList("US", "LR", "MM");

    private static final List<String> UK_SYSTEM = Arrays.asList("GB");

    public static MeasurementSystem getMeasurementSystem(ULocale locale) {
        String country = locale == null ? "" : locale.getCountry().toUpperCase(Locale.ROOT);
        if (US_SYSTEM.contains(country)) return MeasurementSystem.US;
        if (UK_SYSTEM.contains(country)) return MeasurementSystem.UK;
        return MeasurementSystem.SI;
    }
}
