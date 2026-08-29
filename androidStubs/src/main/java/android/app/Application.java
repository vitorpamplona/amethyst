package android.app;

import android.content.ComponentCallbacks2;
import android.content.DelegatingContext;
import android.os.Bundle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JVM stand-in for android.app.Application.
 *
 * Desktop has no Application lifecycle; the entry point calls onCreate()
 * itself. The lifecycle hooks are no-ops here exactly as they are on Android,
 * so a subclass overriding them behaves the same.
 *
 * Activity lifecycle callbacks are a different matter. What the app actually
 * uses them for — "are we in the foreground?" — has a direct desktop analogue
 * in window visibility, so registration is real: callbacks are kept, and the
 * desktop window wiring drives them through {@link #dispatchActivityStarted}
 * and {@link #dispatchActivityStopped}. Until something calls those the
 * registration is inert, which is reported once rather than left silent.
 */
public class Application extends DelegatingContext implements ComponentCallbacks2 {
    /** Same shape as the platform's, with defaults so callers override what they use. */
    public interface ActivityLifecycleCallbacks {
        default void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        default void onActivityStarted(Activity activity) {}

        default void onActivityResumed(Activity activity) {}

        default void onActivityPaused(Activity activity) {}

        default void onActivityStopped(Activity activity) {}

        default void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        default void onActivityDestroyed(Activity activity) {}
    }

    private static final List<ActivityLifecycleCallbacks> LIFECYCLE_CALLBACKS = new CopyOnWriteArrayList<>();

    /**
     * The single "activity" a desktop app has. Callers only ever pass it back
     * to their own handlers, which use it as a Context if at all.
     */
    private static final Activity WINDOW = new Activity();

    public void onCreate() {}

    public void onTerminate() {}

    @Override public void onTrimMemory(int level) {}

    @Override public void onLowMemory() {}

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callbacks) {
        if (callbacks == null) return;
        LIFECYCLE_CALLBACKS.add(callbacks);
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Application.activityLifecycle",
                "desktop has no Activity lifecycle; the window wiring must call "
                        + "Application.dispatchActivityStarted/Stopped on visibility changes for this to fire");
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callbacks) {
        LIFECYCLE_CALLBACKS.remove(callbacks);
    }

    /** Called by the desktop window when it becomes visible. */
    public static void dispatchActivityStarted() {
        for (ActivityLifecycleCallbacks callback : LIFECYCLE_CALLBACKS) callback.onActivityStarted(WINDOW);
    }

    /** Called by the desktop window when it is hidden or minimised. */
    public static void dispatchActivityStopped() {
        for (ActivityLifecycleCallbacks callback : LIFECYCLE_CALLBACKS) callback.onActivityStopped(WINDOW);
    }

    /** Called by the desktop window when it gains focus. */
    public static void dispatchActivityResumed() {
        for (ActivityLifecycleCallbacks callback : LIFECYCLE_CALLBACKS) callback.onActivityResumed(WINDOW);
    }

    /** Called by the desktop window when it loses focus. */
    public static void dispatchActivityPaused() {
        for (ActivityLifecycleCallbacks callback : LIFECYCLE_CALLBACKS) callback.onActivityPaused(WINDOW);
    }

    public void registerComponentCallbacks(Object callbacks) {}

    public void unregisterComponentCallbacks(Object callbacks) {}

    /**
     * Android returns the process name; a desktop app is one process, and the
     * name the app compares against is its own package name.
     */
    public String getProcessName() { return getPackageName(); }
}
