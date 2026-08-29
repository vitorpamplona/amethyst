package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentDispatcher;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JVM stand-in for android.app.PendingIntent.
 *
 * A PendingIntent is a token letting another process act on your behalf, which
 * has no desktop analogue — but its *identity* rules do, and they are what make
 * the callers work. On Android two PendingIntents built from the same kind,
 * request code and matching Intent are the same token, which is how code
 * cancels an alarm or replaces a notification it created earlier. A stub that
 * handed back a fresh object each time would make every `cancel` a silent
 * no-op: the caller would be cancelling a token nothing was ever scheduled
 * against.
 *
 * So this keeps a registry with the platform's matching rules — action, data,
 * type, component and categories, but not extras — and honours the flags that
 * depend on it: {@code FLAG_NO_CREATE} returns null when there is nothing to
 * find, {@code FLAG_UPDATE_CURRENT} swaps in the new extras, and
 * {@code FLAG_CANCEL_CURRENT} drops the old token first.
 *
 * {@link #send} really fires, through {@link IntentDispatcher}.
 */
public final class PendingIntent {
    public static final int FLAG_ONE_SHOT = 0x40000000;
    public static final int FLAG_NO_CREATE = 0x20000000;
    public static final int FLAG_CANCEL_CURRENT = 0x10000000;
    public static final int FLAG_UPDATE_CURRENT = 0x08000000;
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final int FLAG_MUTABLE = 0x02000000;

    private static final Map<Key, PendingIntent> REGISTRY = new ConcurrentHashMap<>();

    private final Key key;
    private volatile Intent intent;
    private final boolean oneShot;

    private PendingIntent(Key key, Intent intent, boolean oneShot) {
        this.key = key;
        this.intent = intent;
        this.oneShot = oneShot;
    }

    public Intent getIntent() { return intent; }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return obtain("activity", requestCode, intent, flags);
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return obtain("broadcast", requestCode, intent, flags);
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return obtain("service", requestCode, intent, flags);
    }

    public static PendingIntent getForegroundService(Context context, int requestCode, Intent intent, int flags) {
        return obtain("service", requestCode, intent, flags);
    }

    private static PendingIntent obtain(String kind, int requestCode, Intent intent, int flags) {
        Key key = new Key(kind, requestCode, intent);

        if ((flags & FLAG_CANCEL_CURRENT) != 0) REGISTRY.remove(key);

        PendingIntent existing = REGISTRY.get(key);
        if (existing != null) {
            if ((flags & FLAG_UPDATE_CURRENT) != 0) existing.intent = intent;
            return existing;
        }

        // FLAG_NO_CREATE asks "does one already exist?" — null is the answer
        // callers branch on to decide there is nothing to cancel.
        if ((flags & FLAG_NO_CREATE) != 0) return null;

        PendingIntent created = new PendingIntent(key, intent, (flags & FLAG_ONE_SHOT) != 0);
        PendingIntent raced = REGISTRY.putIfAbsent(key, created);
        return raced == null ? created : raced;
    }

    /** Fires the wrapped Intent, so a notification action is not a dead button. */
    public void send() {
        if (oneShot) REGISTRY.remove(key, this);
        IntentDispatcher.dispatch(intent);
    }

    public void cancel() { REGISTRY.remove(key, this); }

    /** Android matches on the Intent's identity, not its extras. */
    private static final class Key {
        private final String kind;
        private final int requestCode;
        private final String action;
        private final String data;
        private final String type;
        private final String component;
        private final Object categories;

        Key(String kind, int requestCode, Intent intent) {
            this.kind = kind;
            this.requestCode = requestCode;
            this.action = intent == null ? null : intent.getAction();
            this.data = intent == null || intent.getData() == null ? null : intent.getData().toString();
            this.type = intent == null ? null : intent.getType();
            this.component = intent == null ? null : intent.getComponentClassName();
            this.categories = intent == null ? null : intent.getCategories();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return requestCode == that.requestCode
                    && Objects.equals(kind, that.kind)
                    && Objects.equals(action, that.action)
                    && Objects.equals(data, that.data)
                    && Objects.equals(type, that.type)
                    && Objects.equals(component, that.component)
                    && Objects.equals(categories, that.categories);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, requestCode, action, data, type, component, categories);
        }
    }
}
