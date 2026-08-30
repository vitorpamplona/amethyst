package android.view;

import android.content.Context;

/**
 * JVM stand-in for android.view.ViewGroup.
 *
 * Desktop lays out with Compose, so nothing here measures or positions
 * children. It exists because {@link android.widget.FrameLayout} and the
 * layout-params types are named in signatures the app still compiles against.
 */
public class ViewGroup extends View implements ViewParent {
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

        public int width;
        public int height;

        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public ViewGroup(Context context) { super(context); }

    public void addView(View child) {}

    public void removeView(View child) {}

    public void removeAllViews() {}

    public int getChildCount() { return 0; }
}
