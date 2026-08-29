package android.os;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for android.os.PowerManager.
 *
 * Wake locks exist because Android suspends the CPU and kills background work.
 * Desktop does neither, so a wake lock has nothing to hold: acquire/release are
 * genuinely no-ops rather than unimplemented, and isIgnoringBatteryOptimizations
 * answers true because there is no optimization to be exempt from.
 */
public class PowerManager {
    public static final int PARTIAL_WAKE_LOCK = 1;
    public static final int FULL_WAKE_LOCK = 26;
    public static final int SCREEN_BRIGHT_WAKE_LOCK = 10;

    public class WakeLock {
        public void acquire() {}

        public void acquire(long timeout) {}

        public void release() {}

        public boolean isHeld() { return false; }

        public void setReferenceCounted(boolean value) {}
    }

    public WakeLock newWakeLock(int levelAndFlags, String tag) { return new WakeLock(); }

    /** Nothing suspends a desktop process, so it is never subject to Doze. */
    public boolean isIgnoringBatteryOptimizations(String packageName) { return true; }

    public boolean isPowerSaveMode() { return false; }

    public boolean isInteractive() { return true; }

    static {
        PlatformGaps.declareUnavailable(
                "PowerManager.wakeLock",
                "Android wake locks keep the CPU alive against system suspend. Desktop processes are "
                        + "not suspended or killed for being idle, so there is nothing for a wake lock to hold.");
    }
}
