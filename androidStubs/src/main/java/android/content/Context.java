package android.content;

import android.content.res.Resources;

/**
 * JVM stand-in for android.content.Context.
 *
 * Declares only the surface shared Amethyst code actually calls, so that an
 * unsupported call fails at compile time on the JVM target instead of at
 * runtime. Behaviour lives in the JVM implementation (see
 * com.vitorpamplona.amethyst.shared.platform.JvmContext), not here.
 */
public abstract class Context {
    public abstract String getPackageName();

    public abstract Resources getResources();

    public abstract String getString(int resId);

    public abstract String getString(int resId, Object... formatArgs);
}
