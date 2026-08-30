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
    private ViewParent parent;
    private ViewGroup.LayoutParams layoutParams;

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

    /**
     * Null, always. Compose Desktop has no View tree, so nothing wraps this —
     * and null is what the app's own {@code parent as? DialogWindowProvider}
     * checks for, so the "not inside a dialog" branch is the true one here.
     */
    public ViewParent getParent() { return parent; }

    public void setParent(ViewParent value) { parent = value; }

    public ViewGroup.LayoutParams getLayoutParams() { return layoutParams; }

    public void setLayoutParams(ViewGroup.LayoutParams value) { layoutParams = value; }

    /** Never a layout preview on the JVM; the real thing is always running. */
    public boolean isInEditMode() { return false; }

    public View getRootView() { return this; }

    public interface OnTouchListener {
        boolean onTouch(View view, MotionEvent event);
    }

    public OnTouchListener getOnTouchListener() { return touchListener; }

    /**
     * Kept, never fired: Compose Desktop routes pointer input through its own
     * types, so nothing here synthesises a MotionEvent. Storing it means a
     * desktop host that wanted to bridge gestures has somewhere to deliver.
     */
    public void setOnTouchListener(OnTouchListener listener) { touchListener = listener; }

    private OnTouchListener touchListener;

    public int getWidth() { return 0; }

    public int getHeight() { return 0; }

    public boolean getGlobalVisibleRect(android.graphics.Rect out) { return false; }

    public void getLocationInWindow(int[] out) {}

    public int getBackgroundColor() { return backgroundColor; }

    /** Recorded; Compose paints the desktop window's background, not this. */
    public void setBackgroundColor(int color) { backgroundColor = color; }

    private int backgroundColor;

    public void invalidate() {}

    public void requestLayout() {}
}
