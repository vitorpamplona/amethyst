package android.content;

/**
 * JVM stand-in for android.content.BroadcastReceiver.
 *
 * Desktop has no broadcast bus, so a registered receiver never fires. That is
 * reported where registration happens (Context.sendBroadcast, ContextCompat)
 * rather than here — the class itself is just a shape.
 */
public abstract class BroadcastReceiver {
    public abstract void onReceive(Context context, Intent intent);

    public final PendingResult goAsync() { return new PendingResult(); }

    public static class PendingResult {
        public void finish() {}
    }
}
