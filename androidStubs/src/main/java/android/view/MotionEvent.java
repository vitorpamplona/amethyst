package android.view;

/**
 * JVM stand-in for android.view.MotionEvent.
 *
 * Compose Desktop delivers pointer input through its own types, so nothing
 * here synthesises one. The constants exist because the app's touch handlers
 * switch on them, and they are the platform's own values so a handler ported
 * later keeps meaning the same thing.
 */
public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_MASK = 0xff;

    private final int action;
    private final float x;
    private final float y;

    public MotionEvent(int action, float x, float y) {
        this.action = action;
        this.x = x;
        this.y = y;
    }

    public int getAction() { return action; }

    public int getActionMasked() { return action & ACTION_MASK; }

    public float getX() { return x; }

    public float getY() { return y; }

    public int getPointerCount() { return 1; }
}
