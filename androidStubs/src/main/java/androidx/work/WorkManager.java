package androidx.work;

import android.content.Context;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JVM stand-in for androidx.work.WorkManager.
 *
 * WorkManager exists because Android kills app processes and defers work across
 * reboots. A desktop app that is running can simply run the work, and one that
 * is not running is not running — so the *durable scheduling* half has no
 * counterpart, while the "do this soon, retry on failure" half is an ordinary
 * coroutine.
 *
 * Rather than pretend either way, enqueue records the request and reports it,
 * so the two features that use this (scheduled posts, notification polling) are
 * visibly waiting on a desktop scheduler rather than silently never running. A
 * request enqueued while the app is closed is the part that genuinely cannot
 * work and is declared as such.
 */
public final class WorkManager {
    private static final WorkManager INSTANCE = new WorkManager();

    private final Map<String, WorkRequest> enqueued = new ConcurrentHashMap<>();

    private WorkManager() {}

    public static WorkManager getInstance(Context context) { return INSTANCE; }

    public static WorkManager getInstance() { return INSTANCE; }

    public void enqueue(WorkRequest request) {
        enqueued.put(request.getId().toString(), request);
        report(request.getClass().getSimpleName());
    }

    public void enqueueUniqueWork(String uniqueName, ExistingWorkPolicy policy, WorkRequest request) {
        enqueued.put(uniqueName, request);
        report(uniqueName);
    }

    public void enqueueUniquePeriodicWork(String uniqueName, ExistingPeriodicWorkPolicy policy, WorkRequest request) {
        enqueued.put(uniqueName, request);
        report(uniqueName);
    }

    public void cancelUniqueWork(String uniqueName) { enqueued.remove(uniqueName); }

    public void cancelAllWorkByTag(String tag) {}

    public void cancelWorkById(UUID id) { enqueued.remove(id.toString()); }

    /** What has been asked for but has nowhere to run yet. */
    public List<String> pendingWorkNames() { return List.copyOf(enqueued.keySet()); }

    private static void report(String name) {
        PlatformGaps.report(
                "WorkManager.enqueue",
                "'" + name + "' was scheduled but desktop has no work scheduler yet; "
                        + "in-session work can be a coroutine, across-restart work needs a persisted queue");
    }

    static {
        PlatformGaps.declareUnavailable(
                "WorkManager.deferredAcrossRestart",
                "Android defers work across process death and reboot. A desktop app that is not "
                        + "running cannot run anything; only work scheduled while the app is open has meaning.");
    }
}
