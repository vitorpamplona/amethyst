package androidx.work;

/**
 * JVM stand-in for androidx.work.Constraints.
 *
 * Only the conditions a desktop can actually observe are enforced; see
 * {@link WorkManager} for which ones those are and what happens to the rest.
 */
public final class Constraints {
    public static final Constraints NONE = new Builder().build();

    private final NetworkType requiredNetworkType;
    private final boolean requiresCharging;
    private final boolean requiresBatteryNotLow;
    private final boolean requiresStorageNotLow;
    private final boolean requiresDeviceIdle;

    private Constraints(Builder builder) {
        this.requiredNetworkType = builder.requiredNetworkType;
        this.requiresCharging = builder.requiresCharging;
        this.requiresBatteryNotLow = builder.requiresBatteryNotLow;
        this.requiresStorageNotLow = builder.requiresStorageNotLow;
        this.requiresDeviceIdle = builder.requiresDeviceIdle;
    }

    public NetworkType getRequiredNetworkType() { return requiredNetworkType; }

    public boolean requiresCharging() { return requiresCharging; }

    public boolean requiresBatteryNotLow() { return requiresBatteryNotLow; }

    public boolean requiresStorageNotLow() { return requiresStorageNotLow; }

    public boolean requiresDeviceIdle() { return requiresDeviceIdle; }

    public static final class Builder {
        private NetworkType requiredNetworkType = NetworkType.NOT_REQUIRED;
        private boolean requiresCharging;
        private boolean requiresBatteryNotLow;
        private boolean requiresStorageNotLow;
        private boolean requiresDeviceIdle;

        public Builder setRequiredNetworkType(NetworkType type) {
            this.requiredNetworkType = type;
            return this;
        }

        public Builder setRequiresCharging(boolean value) {
            this.requiresCharging = value;
            return this;
        }

        public Builder setRequiresBatteryNotLow(boolean value) {
            this.requiresBatteryNotLow = value;
            return this;
        }

        public Builder setRequiresStorageNotLow(boolean value) {
            this.requiresStorageNotLow = value;
            return this;
        }

        public Builder setRequiresDeviceIdle(boolean value) {
            this.requiresDeviceIdle = value;
            return this;
        }

        public Constraints build() { return new Constraints(this); }
    }
}
