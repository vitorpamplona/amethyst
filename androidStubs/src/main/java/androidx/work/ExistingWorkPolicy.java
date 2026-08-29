package androidx.work;

/** JVM stand-in for androidx.work.ExistingWorkPolicy. */
public enum ExistingWorkPolicy {
    REPLACE,
    KEEP,
    APPEND,
    APPEND_OR_REPLACE,
    UPDATE,
    CANCEL_AND_REENQUEUE,
}
