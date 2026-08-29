package android.app;

import android.content.ComponentCallbacks2;
import android.content.DelegatingContext;

/**
 * JVM stand-in for android.app.Application.
 *
 * Desktop has no Application lifecycle; the entry point calls onCreate()
 * itself. The lifecycle hooks are no-ops here exactly as they are on Android,
 * so a subclass overriding them behaves the same.
 */
public class Application extends DelegatingContext implements ComponentCallbacks2 {
    public void onCreate() {}

    public void onTerminate() {}

    @Override public void onTrimMemory(int level) {}

    @Override public void onLowMemory() {}

    /** Desktop has no Activity lifecycle to observe; registration is recorded as a gap. */
    public void registerActivityLifecycleCallbacks(Object callbacks) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Application.registerActivityLifecycleCallbacks",
                "desktop has no Activity lifecycle; foreground/background transitions must come from the window");
    }

    public void unregisterActivityLifecycleCallbacks(Object callbacks) {}

    public void registerComponentCallbacks(Object callbacks) {}

    public void unregisterComponentCallbacks(Object callbacks) {}
}
