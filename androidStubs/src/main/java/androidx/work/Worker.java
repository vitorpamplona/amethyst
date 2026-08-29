package androidx.work;

import android.content.Context;
import java.util.concurrent.CompletableFuture;

/**
 * JVM stand-in for androidx.work.Worker: the blocking flavour.
 *
 * {@link WorkManager} already calls {@link #startWork()} on a background
 * thread, so completing the future inline is the same thing the real library
 * does with its synchronous executor.
 */
public abstract class Worker extends ListenableWorker {
    public Worker(Context appContext, WorkerParameters parameters) {
        super(appContext, parameters);
    }

    public abstract Result doWork();

    @Override
    public final CompletableFuture<Result> startWork() {
        try {
            return CompletableFuture.completedFuture(doWork());
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }
}
