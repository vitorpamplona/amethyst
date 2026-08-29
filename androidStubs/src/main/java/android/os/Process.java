package android.os;

/**
 * JVM stand-in for android.os.Process.
 *
 * Thread priorities are real and are applied through {@link Thread}, which is
 * what Android's setThreadPriority ultimately affects; the uid/pid identifiers
 * are process facts the JVM can answer for itself.
 */
public final class Process {
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_LOWEST = 19;

    private Process() {}

    public static int myUid() { return 0; }

    public static int myPid() { return (int) ProcessHandle.current().pid(); }

    public static int myTid() { return (int) Thread.currentThread().threadId(); }

    /**
     * Maps Android's -20..19 nice scale onto Java's 1..10, so a caller asking
     * for a background thread really gets a lower-priority one rather than no
     * change at all.
     */
    public static void setThreadPriority(int priority) {
        Thread.currentThread().setPriority(toJavaPriority(priority));
    }

    public static void setThreadPriority(int tid, int priority) { setThreadPriority(priority); }

    public static int getThreadPriority(int tid) {
        int java = Thread.currentThread().getPriority();
        return Math.round((Thread.MAX_PRIORITY - java) * (39f / 9f)) - 20;
    }

    static int toJavaPriority(int androidPriority) {
        int clamped = Math.max(-20, Math.min(19, androidPriority));
        // -20 -> 10 (highest), 19 -> 1 (lowest).
        int scaled = Math.round(Thread.MAX_PRIORITY - (clamped + 20) * (9f / 39f));
        return Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, scaled));
    }
}
