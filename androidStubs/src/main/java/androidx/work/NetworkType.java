package androidx.work;

/** JVM stand-in for androidx.work.NetworkType. */
public enum NetworkType {
    NOT_REQUIRED,
    CONNECTED,
    UNMETERED,
    NOT_ROAMING,
    METERED,
    TEMPORARILY_UNMETERED,
}
