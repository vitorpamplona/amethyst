package android.view;

import android.content.Context;

/**
 * JVM stand-in for android.view.Window.
 *
 * The flags the app sets here are real requests with real consequences —
 * FLAG_KEEP_SCREEN_ON during video, FLAG_SECURE over the key-backup screen —
 * so they are stored rather than discarded, and the ones the desktop shell has
 * to act on report themselves. FLAG_SECURE in particular must never look
 * applied when it is not: it is the only thing standing between a displayed
 * nsec and a screenshot.
 */
public class Window {
    private final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
    private final View decorView;

    public Window() { this(null); }

    public Window(Context context) { this.decorView = new View(context); }

    public WindowManager.LayoutParams getAttributes() { return params; }

    public View getDecorView() { return decorView; }

    public void setAttributes(WindowManager.LayoutParams value) {
        params.copyFrom(value);
        report();
    }

    public void addFlags(int flags) {
        params.flags |= flags;
        report();
    }

    public void clearFlags(int flags) { params.flags &= ~flags; }

    public void setFlags(int flags, int mask) {
        params.flags = (params.flags & ~mask) | (flags & mask);
        report();
    }

    private void report() {
        if ((params.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "Window.FLAG_KEEP_SCREEN_ON",
                    "the desktop shell must inhibit the screensaver during playback; a per-window flag has no effect here");
        }
        if ((params.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                    "Window.FLAG_SECURE",
                    "the desktop shell must exclude this window from screen capture before it shows a private key; "
                            + "nothing here blocks a screenshot");
        }
    }
}
