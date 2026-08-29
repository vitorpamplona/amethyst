package android.os;

/**
 * JVM stand-in for android.os.BatteryManager.
 *
 * A laptop has a battery the JDK cannot read — there is no portable API for it
 * on any desktop OS. Rather than inventing a level, the properties report
 * unknown and the limitation is declared, so a caller can tell "no battery
 * information" from "battery at 0%".
 */
public class BatteryManager {
    public static final int BATTERY_PROPERTY_CAPACITY = 4;
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_FULL = 5;
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_STATUS = "status";

    /** Android returns Integer.MIN_VALUE for an unknown property; so do we. */
    public int getIntProperty(int id) { return Integer.MIN_VALUE; }

    public long getLongProperty(int id) { return Long.MIN_VALUE; }

    public boolean isCharging() { return true; }

    static {
        com.vitorpamplona.amethyst.stubs.PlatformGaps.declareUnavailable(
                "BatteryManager",
                "No JDK API exposes battery state on any desktop OS, so level and charging status "
                        + "are reported as unknown rather than guessed.");
    }
}
