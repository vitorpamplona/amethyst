package androidx.work;

import java.util.concurrent.TimeUnit;

/** JVM stand-in for androidx.work.PeriodicWorkRequest. */
public final class PeriodicWorkRequest extends WorkRequest {
    public static final class Builder {
        public Builder(Class<? extends Worker> workerClass, long interval, TimeUnit unit) {}

        public Builder setInputData(Data data) { return this; }

        public Builder addTag(String tag) { return this; }

        public Builder setConstraints(Object constraints) { return this; }

        public PeriodicWorkRequest build() { return new PeriodicWorkRequest(); }
    }
}
