package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

/**
 * JVM stand-in for android.graphics.drawable.Drawable.
 *
 * The app's use of Drawable is almost entirely as a carrier: something a
 * loaded image comes back as, which is then either drawn or unwrapped for its
 * bitmap. That much is platform-neutral, so the base holds the bounds and the
 * subclasses do the drawing.
 */
public abstract class Drawable {
    private final Rect bounds = new Rect();

    public void setBounds(int left, int top, int right, int bottom) {
        bounds.set(left, top, right, bottom);
    }

    public void setBounds(Rect rect) {
        if (rect != null) bounds.set(rect.left, rect.top, rect.right, rect.bottom);
    }

    public Rect getBounds() { return bounds; }

    public int getIntrinsicWidth() { return -1; }

    public int getIntrinsicHeight() { return -1; }

    public void setAlpha(int alpha) {}

    public void setColorFilter(Object colorFilter) {}

    public abstract void draw(Canvas canvas);
}
