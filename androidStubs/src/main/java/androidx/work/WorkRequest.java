package androidx.work;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JVM stand-in for androidx.work.WorkRequest.
 *
 * Unlike a placeholder, this actually carries what {@link WorkManager} needs to
 * run the thing later: which worker class to build, what to hand it, when, and
 * under what conditions.
 */
public abstract class WorkRequest {
    /** WorkManager's default first backoff, and its ceiling. */
    public static final long DEFAULT_BACKOFF_DELAY_MILLIS = 30_000L;

    public static final long MAX_BACKOFF_MILLIS = 5 * 60 * 60 * 1000L;

    /**
     * Android's floor for a periodic request. Recorded for reference but not
     * enforced: it is a JobScheduler quota, not part of what the caller meant,
     * and a desktop timer has no such quota. Every caller in this app asks for
     * 15 minutes anyway, so honouring the requested interval changes nothing in
     * production and makes a short interval testable.
     */
    public static final long MIN_PERIODIC_INTERVAL_MILLIS = 15 * 60 * 1000L;

    private final UUID id = UUID.randomUUID();
    private final Class<? extends ListenableWorker> workerClass;
    private final Data inputData;
    private final Constraints constraints;
    private final Set<String> tags;
    private final long initialDelayMillis;
    private final long intervalMillis;

    WorkRequest(
            Class<? extends ListenableWorker> workerClass,
            Data inputData,
            Constraints constraints,
            Set<String> tags,
            long initialDelayMillis,
            long intervalMillis) {
        this.workerClass = workerClass;
        this.inputData = inputData;
        this.constraints = constraints;
        Set<String> all = new LinkedHashSet<>(tags);
        // WorkManager tags every request with its worker's class name.
        if (workerClass != null) all.add(workerClass.getName());
        this.tags = Collections.unmodifiableSet(all);
        this.initialDelayMillis = initialDelayMillis;
        this.intervalMillis = intervalMillis;
    }

    public UUID getId() { return id; }

    public Class<? extends ListenableWorker> getWorkerClass() { return workerClass; }

    public Data getInputData() { return inputData; }

    public Constraints getConstraints() { return constraints; }

    public Set<String> getTags() { return tags; }

    public long getInitialDelayMillis() { return initialDelayMillis; }

    /** Zero for one-time work; the repeat period for periodic work. */
    public long getIntervalMillis() { return intervalMillis; }

    public boolean isPeriodic() { return intervalMillis > 0; }

    /** Shared builder state; the concrete builders only differ in what they produce. */
    abstract static class BaseBuilder {
        final Class<? extends ListenableWorker> workerClass;
        Data inputData = Data.EMPTY;
        Constraints constraints = Constraints.NONE;
        final Set<String> tags = new HashSet<>();
        long initialDelayMillis;

        BaseBuilder(Class<? extends ListenableWorker> workerClass) {
            this.workerClass = workerClass;
        }

        static long toMillis(long duration, TimeUnit unit) { return unit.toMillis(duration); }
    }
}
