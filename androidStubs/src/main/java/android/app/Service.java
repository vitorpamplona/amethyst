package android.app;

import android.content.DelegatingContext;
import android.content.Intent;
import android.os.IBinder;

/**
 * JVM stand-in for android.app.Service.
 *
 * Desktop has no service model — no foreground services, no binding, no
 * restart policy. Subclasses exist in shared code (playback, notifications,
 * calls, the napplet broker) and each is really a long-lived object that the
 * desktop app should own directly. This stub lets them compile and reports any
 * attempt to actually start one, so the port cannot quietly ship a feature
 * whose service never runs.
 */
public abstract class Service extends DelegatingContext {
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int START_REDELIVER_INTENT = 3;

    public static final int STOP_FOREGROUND_REMOVE = 1;
    public static final int STOP_FOREGROUND_DETACH = 2;

    public void onCreate() {}

    public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    public abstract IBinder onBind(Intent intent);

    public boolean onUnbind(Intent intent) { return false; }

    public void onDestroy() {}

    /**
     * Android calls this when the user swipes the app off the recents list.
     * A desktop app closing its window is the nearest thing, and nothing calls
     * it here, so it exists for the overrides in shared code to compile and
     * keep documenting the Android path.
     */
    public void onTaskRemoved(Intent rootIntent) {}

    public void stopSelf() {}

    /**
     * Android calls this when a foreground service exhausts its runtime budget.
     * Desktop has no such budget, so it is never called here — it exists so the
     * overrides in shared code compile and keep documenting the Android path.
     */
    public void onTimeout(int startId) {}

    public void onTimeout(int startId, int foregroundServiceType) { onTimeout(startId); }

    public void stopForeground(int flags) {}

    public void startForeground(int id, Notification notification) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "Service.startForeground",
                getClass().getName() + " expects to run as a foreground service; desktop must own this object directly");
    }

    public void startForeground(int id, Notification notification, int foregroundServiceType) {
        startForeground(id, notification);
    }
}
