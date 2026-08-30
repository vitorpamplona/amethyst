package android.view;

/**
 * JVM stand-in for android.view.WindowManager.
 *
 * The app never adds windows through this; it reaches for
 * {@link LayoutParams} to set per-window flags — keep the screen on, block
 * screenshots, dim behind a dialog, override brightness. Those are real
 * intents, so the params carry real values and {@link Window} is what decides
 * whether the desktop can honour them.
 */
public class WindowManager {
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_DIM_BEHIND = 0x00000002;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_SECURE = 0x00002000;
        public static final int FLAG_SHOW_WHEN_LOCKED = 0x00080000;
        public static final int FLAG_TURN_SCREEN_ON = 0x00200000;

        public static final int TYPE_APPLICATION = 2;

        public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;
        public static final float BRIGHTNESS_OVERRIDE_OFF = 0.0f;
        public static final float BRIGHTNESS_OVERRIDE_FULL = 1.0f;

        public int flags;
        public int type = TYPE_APPLICATION;
        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public float dimAmount;

        public LayoutParams() { super(MATCH_PARENT, MATCH_PARENT); }

        public LayoutParams(int width, int height) { super(width, height); }

        public void copyFrom(LayoutParams other) {
            if (other == null) return;
            width = other.width;
            height = other.height;
            flags = other.flags;
            type = other.type;
            screenBrightness = other.screenBrightness;
            dimAmount = other.dimAmount;
        }
    }
}
