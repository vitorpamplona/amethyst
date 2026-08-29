package android.graphics;

/** JVM stand-in for android.graphics.Rect. Pure geometry, so implemented for real. */
public class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right - left; }

    public int height() { return bottom - top; }

    public boolean isEmpty() { return left >= right || top >= bottom; }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
}
