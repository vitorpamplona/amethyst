package android.app;

/**
 * JVM stand-in for android.app.UiModeManager.
 *
 * The app writes to this to force the whole system UI into day or night mode
 * when the user picks a theme — a system-wide setting on Android, and a
 * per-user desktop-environment setting everywhere else, which an application
 * has no business changing. So writes are declared unavailable rather than
 * accepted silently; the app's own theme still switches, because that is driven
 * by its own preference and not by what comes back from here.
 *
 * Reads report MODE_NIGHT_AUTO, which is the honest "the system decides"
 * answer. Detecting the desktop's current light/dark preference is a separate
 * job for the shell, which has the platform hooks for it.
 */
public class UiModeManager {
    public static final int MODE_NIGHT_AUTO = 0;
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;
    public static final int MODE_NIGHT_CUSTOM = 3;

    public static final int NIGHT_MODE_NO = 1;
    public static final int NIGHT_MODE_YES = 2;

    public int getNightMode() { return MODE_NIGHT_AUTO; }

    public void setNightMode(int mode) {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.unavailable(
                "UiModeManager.setNightMode",
                "day/night is a desktop-environment setting owned by the user, not something an "
                        + "application switches for the whole system. The app's own theme still follows "
                        + "its own preference.");
    }

    public int getCurrentModeType() { return 0; }
}
