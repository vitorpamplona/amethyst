package android.app;

import android.content.Context;
import android.content.Intent;

/**
 * JVM stand-in for android.app.PendingIntent.
 *
 * A PendingIntent is a token letting another process act on your behalf, which
 * has no desktop analogue. It holds its Intent so a desktop notification
 * backend can read the action off it, and `send()` is inert.
 */
public final class PendingIntent {
    public static final int FLAG_ONE_SHOT = 0x40000000;
    public static final int FLAG_NO_CREATE = 0x20000000;
    public static final int FLAG_CANCEL_CURRENT = 0x10000000;
    public static final int FLAG_UPDATE_CURRENT = 0x08000000;
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final int FLAG_MUTABLE = 0x02000000;

    private final Intent intent;

    private PendingIntent(Intent intent) { this.intent = intent; }

    public Intent getIntent() { return intent; }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public void send() {}

    public void cancel() {}
}
