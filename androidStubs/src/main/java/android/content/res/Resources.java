package android.content.res;

/** JVM stand-in for android.content.res.Resources. See Context. */
public abstract class Resources {
    public abstract String getString(int resId);

    public abstract String getString(int resId, Object... formatArgs);

    public abstract String getQuantityString(int resId, int quantity);

    public abstract String getQuantityString(int resId, int quantity, Object... formatArgs);

    public abstract Configuration getConfiguration();

    /**
     * The parser for an {@code R.xml} resource. Real: the app reads its own
     * `locales_config.xml` through this to build the language picker, so a
     * parser over nothing would leave the desktop with no languages to choose.
     */
    public abstract XmlResourceParser getXml(int resId);

    /** Same as the platform's: an id with no resource behind it is a bug, not a blank. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }
}
