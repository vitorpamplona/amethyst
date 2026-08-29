package androidx.health.connect.client;

import android.content.Context;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for Health Connect.
 *
 * This is the clearest case of a feature with no desktop counterpart: Health
 * Connect is an Android system service holding on-device health records written
 * by other apps. Desktop has no such store, so workout import has nothing to
 * read from — not "nothing yet", nothing at all, unless some future desktop OS
 * grows an equivalent.
 *
 * So the availability check answers SDK_UNAVAILABLE, which is a state the app
 * already handles (an Android phone without Health Connect installed reports
 * the same), and the feature hides itself rather than offering an import button
 * that returns nothing.
 */
public interface HealthConnectClient {
    int SDK_UNAVAILABLE = 1;
    int SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED = 2;
    int SDK_AVAILABLE = 3;

    String ACTION_HEALTH_CONNECT_SETTINGS = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS";

    static int getSdkStatus(Context context) {
        PlatformGaps.unavailable(
                "HealthConnect",
                "Health Connect is an Android system service holding on-device health records. "
                        + "Desktop has no equivalent store, so workout import has nothing to read from.");
        return SDK_UNAVAILABLE;
    }

    static int getSdkStatus(Context context, String providerPackageName) { return getSdkStatus(context); }

    static HealthConnectClient getOrCreate(Context context) {
        throw new IllegalStateException("Health Connect is not available on this platform");
    }

    PermissionController getPermissionController();
}
