package android.app;

/**
 * JVM stand-in for android.app.ForegroundServiceStartNotAllowedException.
 *
 * Android throws this when a backgrounded app tries to start a foreground
 * service. Desktop has no foreground services and no such restriction, so it is
 * never thrown here — but the catch blocks that handle it, and their fallbacks,
 * still have to compile.
 */
public class ForegroundServiceStartNotAllowedException extends IllegalStateException {
    public ForegroundServiceStartNotAllowedException() { super(); }

    public ForegroundServiceStartNotAllowedException(String message) { super(message); }
}
