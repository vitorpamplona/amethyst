package androidx.work;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** JVM stand-in for androidx.work.PeriodicWorkRequest. */
public final class PeriodicWorkRequest extends WorkRequest {
    private PeriodicWorkRequest(Builder builder) {
        super(
                builder.workerClass,
                builder.inputData,
                builder.constraints,
                builder.tags,
                builder.initialDelayMillis,
                builder.intervalMillis);
    }

    public static final class Builder extends BaseBuilder {
        private final long intervalMillis;

        public Builder(Class<? extends ListenableWorker> workerClass, long interval, TimeUnit unit) {
            super(workerClass);
            this.intervalMillis = toMillis(interval, unit);
        }

        public Builder(Class<? extends ListenableWorker> workerClass, Duration interval) {
            super(workerClass);
            this.intervalMillis = interval.toMillis();
        }

        public Builder(
                Class<? extends ListenableWorker> workerClass,
                long interval,
                TimeUnit unit,
                long flexInterval,
                TimeUnit flexUnit) {
            this(workerClass, interval, unit);
        }

        public Builder setInputData(Data data) {
            inputData = data;
            return this;
        }

        public Builder setInitialDelay(long duration, TimeUnit unit) {
            initialDelayMillis = toMillis(duration, unit);
            return this;
        }

        public Builder setInitialDelay(Duration duration) {
            initialDelayMillis = duration.toMillis();
            return this;
        }

        public Builder addTag(String tag) {
            tags.add(tag);
            return this;
        }

        public Builder setConstraints(Constraints value) {
            constraints = value;
            return this;
        }

        public Builder setBackoffCriteria(Object policy, long duration, TimeUnit unit) { return this; }

        public PeriodicWorkRequest build() { return new PeriodicWorkRequest(this); }
    }
}
