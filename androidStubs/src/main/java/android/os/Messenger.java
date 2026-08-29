package android.os;

/**
 * JVM stand-in for android.os.Messenger.
 *
 * Messenger exists to hand a message channel across a process boundary. The
 * desktop app is a single process, so this carries no cross-process behaviour;
 * it exists for the napplet IPC code to compile, and that feature is expected
 * to be gated off on desktop rather than emulated.
 */
public final class Messenger {
    private final Handler handler;

    public Messenger(Handler target) { this.handler = target; }

    public Messenger(IBinder target) { this.handler = null; }

    public void send(Message message) {}

    public IBinder getBinder() { return null; }
}
