package android.view;

import android.content.Context;

/**
 * JVM stand-in for android.view.View.
 *
 * Desktop draws with Compose, not a View tree. This exists because a few types
 * still name View in a signature; nothing here lays out or draws, and code that
 * actually needs a platform view is excluded from the desktop build instead.
 */
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    private final Context context;
    private int visibility = VISIBLE;
    private boolean keepScreenOn;

    public View(Context context) { this.context = context; }

    public Context getContext() { return context; }

    public int getVisibility() { return visibility; }

    public void setVisibility(int value) { visibility = value; }

    public boolean getKeepScreenOn() { return keepScreenOn; }

    /**
     * Desktop screensavers are a user setting, not something an app overrides
     * per-view, so this records the intent without acting on it. Video playback
     * that wants to inhibit sleep should ask the desktop shell.
     */
    public void setKeepScreenOn(boolean value) { keepScreenOn = value; }

    public int getWidth() { return 0; }

    public int getHeight() { return 0; }

    public boolean getGlobalVisibleRect(android.graphics.Rect out) { return false; }

    public void getLocationInWindow(int[] out) {}

    public void invalidate() {}

    public void requestLayout() {}
}
