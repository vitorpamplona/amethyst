package androidx.core.os;

import java.util.Locale;

/** JVM stand-in for androidx.core.os.LocaleListCompat. Pure data, so implemented for real. */
public final class LocaleListCompat {
    private static final LocaleListCompat EMPTY = new LocaleListCompat(new Locale[0]);

    private final Locale[] locales;

    private LocaleListCompat(Locale[] locales) { this.locales = locales; }

    public static LocaleListCompat getEmptyLocaleList() { return EMPTY; }

    public static LocaleListCompat create(Locale... locales) { return new LocaleListCompat(locales); }

    public static LocaleListCompat forLanguageTags(String tags) {
        if (tags == null || tags.isEmpty()) return EMPTY;
        String[] parts = tags.split(",");
        Locale[] out = new Locale[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Locale.forLanguageTag(parts[i].trim());
        return new LocaleListCompat(out);
    }

    public static LocaleListCompat getDefault() { return create(Locale.getDefault()); }

    public Locale get(int index) { return index >= 0 && index < locales.length ? locales[index] : null; }

    public boolean isEmpty() { return locales.length == 0; }

    public int size() { return locales.length; }

    public String toLanguageTags() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < locales.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(locales[i].toLanguageTag());
        }
        return sb.toString();
    }
}
