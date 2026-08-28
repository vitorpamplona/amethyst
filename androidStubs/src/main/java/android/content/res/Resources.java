package android.content.res;

/** JVM stand-in for android.content.res.Resources. See Context. */
public abstract class Resources {
    public abstract String getString(int resId);

    public abstract String getString(int resId, Object... formatArgs);

    public abstract String getQuantityString(int resId, int quantity);

    public abstract String getQuantityString(int resId, int quantity, Object... formatArgs);

    public abstract Configuration getConfiguration();
}
