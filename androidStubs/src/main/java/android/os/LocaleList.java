package android.os;

import java.util.Locale;

/**
 * JVM stand-in for android.os.LocaleList.
 *
 * Unlike the other stubs this one carries its own (trivial) implementation:
 * it is a plain ordered list of locales with no platform behaviour to defer to.
 */
public final class LocaleList {
    private final Locale[] locales;

    public LocaleList(Locale... locales) {
        this.locales = locales;
    }

    public Locale get(int index) {
        return index >= 0 && index < locales.length ? locales[index] : null;
    }

    public boolean isEmpty() {
        return locales.length == 0;
    }

    public int size() {
        return locales.length;
    }
}
