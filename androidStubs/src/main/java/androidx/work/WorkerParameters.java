package androidx.work;

import java.util.Collections;
import java.util.Set;

/** JVM stand-in for androidx.work.WorkerParameters. */
public class WorkerParameters {
    private final Data inputData;
    private final int runAttemptCount;
    private final Set<String> tags;

    public WorkerParameters() { this(Data.EMPTY, 0, Collections.emptySet()); }

    public WorkerParameters(Data inputData, int runAttemptCount, Set<String> tags) {
        this.inputData = inputData;
        this.runAttemptCount = runAttemptCount;
        this.tags = tags;
    }

    public Data getInputData() { return inputData; }

    public int getRunAttemptCount() { return runAttemptCount; }

    public Set<String> getTags() { return tags; }
}
