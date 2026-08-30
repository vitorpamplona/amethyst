package android.icu.util;

import java.util.Locale;

/**
 * JVM stand-in for android.icu.util.ULocale.
 *
 * Android bundles ICU4J; the JDK does not. Only the wrapping is needed here —
 * the one thing the app asks ICU is which measurement system a locale uses, and
 * that lives in {@link android.icu.text.LocaleData}.
 */
public final class ULocale {
    private final Locale locale;

    private ULocale(Locale locale) { this.locale = locale; }

    public static ULocale forLocale(Locale locale) { return new ULocale(locale); }

    public static ULocale forLanguageTag(String tag) { return new ULocale(Locale.forLanguageTag(tag)); }

    public Locale toLocale() { return locale; }

    public String getCountry() { return locale.getCountry(); }

    public String getLanguage() { return locale.getLanguage(); }

    @Override public String toString() { return locale.toLanguageTag(); }
}
