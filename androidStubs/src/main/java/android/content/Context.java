package android.content;

/**
 * JVM stand-in for android.content.Context.
 *
 * Only the surface that shared Amethyst code actually calls is declared here.
 * Everything else is intentionally absent so that an unsupported call fails at
 * compile time on the JVM target rather than at runtime.
 */
public abstract class Context {
    public abstract String getPackageName();

    public abstract String getString(int resId);

    public abstract String getString(int resId, Object... formatArgs);
}
