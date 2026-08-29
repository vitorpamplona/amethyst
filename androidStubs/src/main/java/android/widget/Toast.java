package android.widget;

import android.content.Context;

/**
 * JVM stand-in for android.widget.Toast.
 *
 * Toast has real user-visible behaviour, so rather than being inert it routes
 * through a delegate the desktop app installs (a snackbar, a tray balloon —
 * whatever fits). Until one is installed, showing a toast is a no-op rather
 * than a crash: a missing transient notice must never take down a screen.
 */
public final class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    /** Installed by the desktop app; see PlatformToasts. */
    public interface Presenter {
        void show(CharSequence text, int duration);
    }

    private static volatile Presenter presenter;

    public static void setPresenter(Presenter value) { presenter = value; }

    private final CharSequence text;
    private final int duration;

    private Toast(CharSequence text, int duration) {
        this.text = text;
        this.duration = duration;
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        return new Toast(text, duration);
    }

    public static Toast makeText(Context context, int resId, int duration) {
        return new Toast(context == null ? "" : context.getString(resId), duration);
    }

    public void show() {
        Presenter p = presenter;
        if (p != null) p.show(text, duration);
    }

    public void cancel() {}

    public void setGravity(int gravity, int xOffset, int yOffset) {}
}
