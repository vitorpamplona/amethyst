package android.view;

/**
 * JVM stand-in for android.view.Window.
 *
 * The app reaches for this to hold the screen awake and to set brightness
 * during fullscreen video. Both are desktop-shell concerns rather than
 * per-window flags, so the values are recorded and reported once — a video that
 * lets the display sleep mid-playback is a real bug, not a cosmetic one.
 */
public class Window {
    public static final class LayoutParams {
        public static final int FLAG_KEEP_SCREEN_ON = 128;
        public static final int BRIGHTNESS_OVERRIDE_NONE = -1;

        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public int flags;
    }

    private final LayoutParams params = new LayoutParams();

    public LayoutParams getAttributes() { return params; }

    public void setAttributes(LayoutParams value) {
        params.screenBrightness = value.screenBrightness;
        params.flags = value.flags;
        report();
    }

    public void addFlags(int flags) {
        params.flags |= flags;
        report();
    }

    public void clearFlags(int flags) { params.flags &= ~flags; }

    private void report() {
        if ((params.flags & LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "Window.FLAG_KEEP_SCREEN_ON",
                    "the desktop shell must inhibit the screensaver during playback; a per-window flag has no effect here");
        }
    }
}
