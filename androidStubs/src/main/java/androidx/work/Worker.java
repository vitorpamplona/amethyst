package androidx.work;

import android.content.Context;

/** JVM stand-in for androidx.work.Worker and its coroutine sibling. */
public abstract class Worker {
    private final Context context;
    private final WorkerParameters parameters;

    public Worker(Context context, WorkerParameters parameters) {
        this.context = context;
        this.parameters = parameters;
    }

    public Context getApplicationContext() { return context; }

    public WorkerParameters getParams() { return parameters; }

    public Data getInputData() { return parameters.getInputData(); }

    public int getRunAttemptCount() { return parameters.getRunAttemptCount(); }

    public java.util.Set<String> getTags() { return parameters.getTags(); }

    public abstract Result doWork();

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

        public Data getOutputData() { return output; }
    }
}
