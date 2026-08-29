package androidx.work;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** JVM stand-in for androidx.work.OneTimeWorkRequest. */
public final class OneTimeWorkRequest extends WorkRequest {
    private OneTimeWorkRequest(Builder builder) {
        super(builder.workerClass, builder.inputData, builder.constraints, builder.tags, builder.initialDelayMillis, 0L);
    }

    public static final class Builder extends BaseBuilder {
        public Builder(Class<? extends ListenableWorker> workerClass) { super(workerClass); }

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

        public OneTimeWorkRequest build() { return new OneTimeWorkRequest(this); }
    }
}
