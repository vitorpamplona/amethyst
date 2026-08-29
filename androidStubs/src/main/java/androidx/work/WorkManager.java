package androidx.work;

import android.content.Context;
import android.net.ConnectivityManager;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * JVM stand-in for androidx.work.WorkManager that actually runs the work.
 *
 * Scheduled posts and calendar reminders are not decorative: a stand-in that
 * records an enqueue and never fires means a post the user scheduled silently
 * never publishes. So this is a real scheduler — a daemon timer thread that
 * builds the worker, waits on its constraints, runs it, and honours the result:
 *
 * <ul>
 *   <li><b>Periodic</b> work runs once as soon as it is enqueued (after any
 *       initial delay) and then every interval, matching WorkManager, which
 *       also fires the first execution inside the first period.
 *   <li><b>{@code Result.retry()}</b> re-runs with WorkManager's own
 *       exponential backoff — 30s doubling to a 5h ceiling — with
 *       {@code runAttemptCount} incremented, so a worker's own attempt limits
 *       behave the same here.
 *   <li><b>Constraints</b> that the JVM can observe are enforced. A run whose
 *       network constraint is unmet is deferred and re-checked rather than
 *       burning a run attempt, which is what Android's constraint tracking
 *       does. Conditions the JDK cannot see are reported as gaps, once, rather
 *       than being silently treated as satisfied.
 *   <li><b>Unique-work policies</b> KEEP / REPLACE / UPDATE /
 *       CANCEL_AND_REENQUEUE are implemented exactly.
 * </ul>
 *
 * What genuinely differs from Android is the process lifetime: Android's
 * JobScheduler can wake a dead app, and no desktop OS gives an application that
 * for free. Work enqueued in a session that ends is therefore gone, and the app
 * re-enqueues at startup (the scheduled-post store observer already does), which
 * makes "next launch" the desktop analogue of "next boot". That difference is
 * declared, not papered over.
 */
public final class WorkManager {
    /** How long to wait before re-testing a constraint that was not met. */
    private static final long CONSTRAINT_RECHECK_MILLIS = 30_000L;

    private static volatile WorkManager instance;

    private volatile Context appContext;

    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "work-manager");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final Map<String, Entry> unique = new ConcurrentHashMap<>();
    private final Map<UUID, Entry> byId = new ConcurrentHashMap<>();

    /** Package-private so a test can drive an isolated scheduler. */
    WorkManager(Context appContext) {
        this.appContext = appContext;
        PlatformGaps.declareUnavailable(
                "WorkManager.wakeWhileAppClosed",
                "Android's JobScheduler can start a stopped app to run deferred work; no desktop OS "
                        + "offers that to an ordinary application. Work runs while the app is open, and "
                        + "the app re-enqueues what is still due at the next launch.");
    }

    public static WorkManager getInstance(Context context) {
        WorkManager manager = getInstance();
        if (context != null) manager.appContext = context;
        return manager;
    }

    public static synchronized WorkManager getInstance() {
        if (instance == null) instance = new WorkManager(null);
        return instance;
    }

    public void enqueue(WorkRequest request) { start(request.getId().toString(), request, true); }

    public void enqueueUniqueWork(String uniqueName, ExistingWorkPolicy policy, WorkRequest request) {
        enqueueUnique(uniqueName, keepExisting(policy), request);
    }

    public void enqueueUniquePeriodicWork(String uniqueName, ExistingPeriodicWorkPolicy policy, WorkRequest request) {
        enqueueUnique(uniqueName, keepExisting(policy), request);
    }

    private void enqueueUnique(String uniqueName, boolean keep, WorkRequest request) {
        if (keep && unique.containsKey(uniqueName)) return;
        cancelUniqueWork(uniqueName);
        start(uniqueName, request, false);
    }

    private static boolean keepExisting(ExistingWorkPolicy policy) {
        switch (policy) {
            case KEEP:
                return true;
            case APPEND:
            case APPEND_OR_REPLACE:
                reportAppendUnsupported();
                return false;
            default:
                return false;
        }
    }

    private static boolean keepExisting(ExistingPeriodicWorkPolicy policy) {
        switch (policy) {
            case KEEP:
                return true;
            case APPEND:
            case APPEND_OR_REPLACE:
                reportAppendUnsupported();
                return false;
            default:
                return false;
        }
    }

    private static void reportAppendUnsupported() {
        PlatformGaps.report(
                "WorkManager.appendPolicy",
                "APPEND chains a request behind the existing one; this scheduler has no work chains "
                        + "and replaces instead. Nothing in the app uses APPEND today.");
    }

    private void start(String name, WorkRequest request, boolean transientEntry) {
        Entry entry = new Entry(name, request, appContext);
        if (!transientEntry) unique.put(name, entry);
        byId.put(request.getId(), entry);
        schedule(entry, request.getInitialDelayMillis());
    }

    public void cancelUniqueWork(String uniqueName) { stop(unique.remove(uniqueName)); }

    public void cancelWorkById(UUID id) {
        Entry entry = byId.get(id);
        if (entry != null) cancelEntry(entry);
    }

    public void cancelAllWorkByTag(String tag) {
        for (Entry entry : new ArrayList<>(unique.values())) {
            if (entry.request.getTags().contains(tag)) cancelEntry(entry);
        }
    }

    public void cancelAllWork() {
        for (Entry entry : new ArrayList<>(unique.values())) cancelEntry(entry);
    }

    private void cancelEntry(Entry entry) {
        unique.remove(entry.name, entry);
        stop(entry);
    }

    private void stop(Entry entry) {
        if (entry == null) return;
        entry.cancelled = true;
        byId.remove(entry.request.getId());
        ScheduledFuture<?> next = entry.next;
        if (next != null) next.cancel(false);
        ListenableWorker running = entry.running;
        if (running != null) running.onStopped();
    }

    /** What is scheduled right now, for a diagnostics screen or a test. */
    public List<String> pendingWorkNames() { return List.copyOf(unique.keySet()); }

    /** True while {@code uniqueName} is scheduled or running. */
    public boolean isScheduled(String uniqueName) { return unique.containsKey(uniqueName); }

    private void schedule(Entry entry, long delayMillis) {
        if (entry.cancelled) return;
        entry.next = timer.schedule(() -> run(entry), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    private void run(Entry entry) {
        if (entry.cancelled) return;

        if (!constraintsMet(entry.request.getConstraints())) {
            // Deferring rather than failing keeps runAttemptCount meaningful: it
            // counts the worker's own failures, not the platform's waiting.
            schedule(entry, CONSTRAINT_RECHECK_MILLIS);
            return;
        }

        ListenableWorker worker;
        try {
            worker = newWorker(entry);
        } catch (Exception e) {
            System.err.println("[work-manager] cannot construct " + entry.request.getWorkerClass() + ": " + e);
            finish(entry, null, e);
            return;
        }

        entry.running = worker;
        CompletableFuture<ListenableWorker.Result> future;
        try {
            future = worker.startWork();
        } catch (Throwable t) {
            finish(entry, null, t);
            return;
        }
        future.whenComplete((result, error) -> finish(entry, result, error));
    }

    private ListenableWorker newWorker(Entry entry) throws Exception {
        WorkerParameters parameters =
                new WorkerParameters(
                        entry.request.getId(),
                        entry.request.getInputData(),
                        entry.attempt,
                        entry.request.getTags());
        return entry.request
                .getWorkerClass()
                .getConstructor(Context.class, WorkerParameters.class)
                .newInstance(entry.context, parameters);
    }

    private void finish(Entry entry, ListenableWorker.Result result, Throwable error) {
        entry.running = null;
        if (entry.cancelled) return;

        if (error != null) {
            // Android treats a worker that throws as a failure, not a retry.
            System.err.println("[work-manager] " + entry.name + " threw: " + error);
        }

        boolean retry = result != null && result.isRetry();
        if (retry) {
            entry.attempt++;
            schedule(entry, backoffMillis(entry.attempt));
            return;
        }

        entry.attempt = 0;
        if (entry.request.isPeriodic()) {
            schedule(entry, entry.request.getIntervalMillis());
        } else {
            unique.remove(entry.name, entry);
            byId.remove(entry.request.getId());
        }
    }

    /** WorkManager's own exponential policy: 30s doubling, capped at 5h. */
    static long backoffMillis(int attempt) {
        long delay = WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS << Math.min(attempt - 1, 31);
        return Math.min(Math.max(delay, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS), WorkRequest.MAX_BACKOFF_MILLIS);
    }

    static boolean constraintsMet(Constraints constraints) {
        if (constraints == null) return true;

        if (constraints.requiresCharging()) {
            PlatformGaps.report(
                    "WorkManager.requiresCharging",
                    "the JDK exposes no charging state; the constraint is treated as met");
        }
        if (constraints.requiresDeviceIdle()) {
            PlatformGaps.unavailable(
                    "WorkManager.requiresDeviceIdle",
                    "a desktop has no doze/idle maintenance window to wait for; the constraint is treated as met");
        }
        if (constraints.requiresStorageNotLow() && lowOnDisk()) return false;

        switch (constraints.getRequiredNetworkType()) {
            case NOT_REQUIRED:
                return true;
            case UNMETERED:
            case TEMPORARILY_UNMETERED:
                // ConnectivityManager already declares that the JDK cannot see
                // metering and assumes unmetered, so this reduces to "online".
                return ConnectivityManager.isConnected();
            default:
                return ConnectivityManager.isConnected();
        }
    }

    private static boolean lowOnDisk() {
        java.io.File home = new java.io.File(System.getProperty("user.home", "."));
        long free = home.getUsableSpace();
        // Matches the spirit of Android's storage-not-low signal: a few hundred
        // MB left is the point at which writing anything gets risky.
        return free > 0 && free < 256L * 1024 * 1024;
    }

    private static final class Entry {
        final String name;
        final WorkRequest request;
        final Context context;
        volatile ScheduledFuture<?> next;
        volatile ListenableWorker running;
        volatile boolean cancelled;
        int attempt;

        Entry(String name, WorkRequest request, Context context) {
            this.name = name;
            this.request = request;
            this.context = context;
        }
    }
}
