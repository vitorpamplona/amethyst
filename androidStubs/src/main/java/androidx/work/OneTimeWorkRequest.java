package androidx.work;

import java.util.concurrent.TimeUnit;

/** JVM stand-in for androidx.work.OneTimeWorkRequest. */
public final class OneTimeWorkRequest extends WorkRequest {
    public static final class Builder {
        private Data inputData = Data.EMPTY;

        public Builder(Class<? extends Worker> workerClass) {}

        public Builder setInputData(Data data) {
            inputData = data;
            return this;
        }

        public Builder setInitialDelay(long duration, TimeUnit unit) { return this; }

        public Builder addTag(String tag) { return this; }

        public Builder setConstraints(Object constraints) { return this; }

        public OneTimeWorkRequest build() { return new OneTimeWorkRequest(); }
    }
}
