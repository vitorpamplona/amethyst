package android.os;

/**
 * JVM stand-in for android.os.Looper.
 *
 * Code reaches for this almost entirely to ask "am I on the main thread". On
 * the JVM the equivalent is the AWT event dispatch thread, which Compose
 * Desktop renders on, so that is what the answer is based on.
 */
public final class Looper {
    private static final Looper MAIN = new Looper();

    private Looper() {}

    public static Looper getMainLooper() { return MAIN; }

    public static Looper myLooper() {
        return java.awt.EventQueue.isDispatchThread() ? MAIN : null;
    }

    public static boolean isMainThread() { return java.awt.EventQueue.isDispatchThread(); }

    public Thread getThread() { return Thread.currentThread(); }
}
