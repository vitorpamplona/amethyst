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

    public void onCreate() {}

    public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    public abstract IBinder onBind(Intent intent);

    public boolean onUnbind(Intent intent) { return false; }

    public void onDestroy() {}

    public void stopSelf() {}

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
