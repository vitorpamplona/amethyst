package androidx.work;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** JVM stand-in for androidx.work.WorkerParameters. */
public class WorkerParameters {
    private final UUID id;
    private final Data inputData;
    private final int runAttemptCount;
    private final Set<String> tags;

    public WorkerParameters() { this(UUID.randomUUID(), Data.EMPTY, 0, Collections.emptySet()); }

    public WorkerParameters(Data inputData, int runAttemptCount, Set<String> tags) {
        this(UUID.randomUUID(), inputData, runAttemptCount, tags);
    }

    public WorkerParameters(UUID id, Data inputData, int runAttemptCount, Set<String> tags) {
        this.id = id;
        this.inputData = inputData;
        this.runAttemptCount = runAttemptCount;
        this.tags = tags;
    }

    public UUID getId() { return id; }

    public Data getInputData() { return inputData; }

    public int getRunAttemptCount() { return runAttemptCount; }

    public Set<String> getTags() { return tags; }
}
