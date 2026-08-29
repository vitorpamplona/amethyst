package androidx.work;

/** JVM stand-in for androidx.work.ExistingPeriodicWorkPolicy. */
public enum ExistingPeriodicWorkPolicy {
    REPLACE,
    KEEP,
    APPEND,
    APPEND_OR_REPLACE,
    UPDATE,
    CANCEL_AND_REENQUEUE,
}
