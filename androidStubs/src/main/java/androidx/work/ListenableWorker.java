package androidx.work;

import android.content.Context;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * JVM stand-in for androidx.work.ListenableWorker: the base every worker
 * extends, and the only thing {@link WorkManager} knows how to run.
 *
 * The future-returning {@link #startWork()} is what makes an asynchronous
 * worker expressible from Java — the scheduler never has to know that
 * {@code CoroutineWorker} suspends, exactly as in the real library.
 */
public abstract class ListenableWorker {
    private final Context appContext;
    private final WorkerParameters parameters;

    public ListenableWorker(Context appContext, WorkerParameters parameters) {
        this.appContext = appContext;
        this.parameters = parameters;
    }

    public final Context getApplicationContext() { return appContext; }

    public final UUID getId() { return parameters.getId(); }

    public final Data getInputData() { return parameters.getInputData(); }

    public final int getRunAttemptCount() { return parameters.getRunAttemptCount(); }

    public final Set<String> getTags() { return parameters.getTags(); }

    /** Starts the work. The returned future completes with the outcome. */
    public abstract CompletableFuture<Result> startWork();

    /** Called when the work is cancelled while running. */
    public void onStopped() {}

    /** JVM stand-in for androidx.work.ListenableWorker.Result. */
    public static final class Result {
        private final String kind;
        private final Data output;

        private Result(String kind, Data output) {
            this.kind = kind;
            this.output = output;
        }

        public static Result success() { return new Result("success", Data.EMPTY); }

        public static Result success(Data output) { return new Result("success", output); }

        public static Result failure() { return new Result("failure", Data.EMPTY); }

        public static Result failure(Data output) { return new Result("failure", output); }

        public static Result retry() { return new Result("retry", Data.EMPTY); }

        public boolean isSuccess() { return "success".equals(kind); }

        public boolean isFailure() { return "failure".equals(kind); }

        public boolean isRetry() { return "retry".equals(kind); }

        public Data getOutputData() { return output; }

        @Override
        public String toString() { return "Result." + kind; }
    }
}
