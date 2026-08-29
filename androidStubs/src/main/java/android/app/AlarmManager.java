package android.app;

import android.os.SystemClock;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * JVM stand-in for android.app.AlarmManager, on a daemon timer.
 *
 * The app uses alarms for one thing — a watchdog that re-checks a long-lived
 * service every few minutes — and "run this again later" is something a desktop
 * process does perfectly well. So the alarm really fires: the scheduled
 * {@link PendingIntent} is sent at its time, and the repeating variants keep
 * repeating until cancelled.
 *
 * The difference from Android is the same one {@link androidx.work.WorkManager}
 * has: the platform's alarm can wake a stopped app, and no desktop OS offers an
 * ordinary application that. An alarm therefore lives as long as the process,
 * which for a watchdog over an in-process service is exactly the lifetime that
 * matters — the thing it watches is gone too. That is declared, not assumed.
 *
 * The `_WAKEUP` and exact/inexact distinctions are about not draining a phone
 * battery. A desktop timer has no such trade-off to make, so all four variants
 * schedule the same way.
 */
public class AlarmManager {
    public static final int RTC = 1;
    public static final int RTC_WAKEUP = 0;
    public static final int ELAPSED_REALTIME = 3;
    public static final int ELAPSED_REALTIME_WAKEUP = 2;

    public static final long INTERVAL_FIFTEEN_MINUTES = 15 * 60 * 1000L;
    public static final long INTERVAL_HALF_HOUR = 30 * 60 * 1000L;
    public static final long INTERVAL_HOUR = 60 * 60 * 1000L;
    public static final long INTERVAL_HALF_DAY = 12 * 60 * 60 * 1000L;
    public static final long INTERVAL_DAY = 24 * 60 * 60 * 1000L;

    private static final ScheduledExecutorService TIMER =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "alarm-manager");
                        thread.setDaemon(true);
                        return thread;
                    });

    /** One entry per PendingIntent, as on Android: re-scheduling replaces. */
    private static final Map<PendingIntent, ScheduledFuture<?>> SCHEDULED = new ConcurrentHashMap<>();

    static {
        PlatformGaps.declareUnavailable(
                "AlarmManager.wakeWhileAppClosed",
                "Android alarms can start a stopped app and wake a sleeping device. No desktop OS "
                        + "offers that to an ordinary application, so an alarm lives as long as the "
                        + "process — which is the lifetime that matters for what these alarms watch.");
    }

    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        scheduleOnce(type, triggerAtMillis, operation);
    }

    public void setExact(int type, long triggerAtMillis, PendingIntent operation) {
        scheduleOnce(type, triggerAtMillis, operation);
    }

    public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        scheduleOnce(type, triggerAtMillis, operation);
    }

    public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        scheduleOnce(type, triggerAtMillis, operation);
    }

    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, PendingIntent operation) {
        scheduleOnce(type, windowStartMillis, operation);
    }

    public void setRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
        scheduleRepeating(type, triggerAtMillis, intervalMillis, operation);
    }

    public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
        scheduleRepeating(type, triggerAtMillis, intervalMillis, operation);
    }

    public void cancel(PendingIntent operation) {
        if (operation == null) return;
        ScheduledFuture<?> scheduled = SCHEDULED.remove(operation);
        if (scheduled != null) scheduled.cancel(false);
    }

    public boolean canScheduleExactAlarms() { return true; }

    /** True while {@code operation} has an alarm pending. For diagnostics. */
    public boolean isScheduled(PendingIntent operation) { return SCHEDULED.containsKey(operation); }

    private void scheduleOnce(int type, long triggerAtMillis, PendingIntent operation) {
        if (operation == null) return;
        cancel(operation);
        SCHEDULED.put(
                operation,
                TIMER.schedule(() -> fire(operation, false), delayFor(type, triggerAtMillis), TimeUnit.MILLISECONDS));
    }

    private void scheduleRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
        if (operation == null) return;
        cancel(operation);
        long period = Math.max(1_000L, intervalMillis);
        SCHEDULED.put(
                operation,
                TIMER.scheduleWithFixedDelay(
                        () -> fire(operation, true),
                        delayFor(type, triggerAtMillis),
                        period,
                        TimeUnit.MILLISECONDS));
    }

    /**
     * The trigger time is on the clock the type names: wall clock for RTC,
     * uptime for ELAPSED_REALTIME. Reading one as the other would schedule a
     * five-minute watchdog fifty years out.
     */
    private static long delayFor(int type, long triggerAtMillis) {
        long now = (type == RTC || type == RTC_WAKEUP) ? System.currentTimeMillis() : SystemClock.elapsedRealtime();
        return Math.max(0L, triggerAtMillis - now);
    }

    private void fire(PendingIntent operation, boolean repeating) {
        if (!repeating) SCHEDULED.remove(operation);
        try {
            operation.send();
        } catch (Exception e) {
            System.err.println("[alarm-manager] alarm delivery failed: " + e);
        }
    }
}
