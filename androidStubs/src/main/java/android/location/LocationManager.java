package android.location;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.Arrays;
import java.util.List;

/**
 * JVM stand-in for android.location.LocationManager.
 *
 * Desktop machines have no GPS and no fused location provider. A desktop app
 * can still know where it is — the user can pick a place, or the app can look
 * up its own IP — but both are application decisions rather than a device
 * reading, so they go through a {@link Provider} the app installs. Nothing is
 * invented here: with no provider, location is simply absent, which is what a
 * caller must already handle on a phone with location switched off.
 */
public class LocationManager {
    public static final String GPS_PROVIDER = "gps";
    public static final String NETWORK_PROVIDER = "network";
    public static final String FUSED_PROVIDER = "fused";
    public static final String PASSIVE_PROVIDER = "passive";

    public interface Provider {
        Location current();
    }

    private static volatile Provider provider;

    public static void setProvider(Provider value) { provider = value; }

    public Location getLastKnownLocation(String providerName) {
        Provider installed = provider;
        if (installed == null) {
            PlatformGaps.report(
                    "LocationManager.getLastKnownLocation",
                    "no desktop location provider installed; a picker or IP lookup can supply one");
            return null;
        }
        return installed.current();
    }

    public boolean isProviderEnabled(String providerName) { return provider != null; }

    public List<String> getAllProviders() { return Arrays.asList(NETWORK_PROVIDER, FUSED_PROVIDER); }

    public void requestLocationUpdates(String providerName, long minTimeMs, float minDistanceM, LocationListener listener) {
        Provider installed = provider;
        if (installed == null) {
            PlatformGaps.report("LocationManager.requestLocationUpdates", "no desktop location provider installed");
            return;
        }
        Location current = installed.current();
        if (current != null) listener.onLocationChanged(current);
    }

    public void removeUpdates(LocationListener listener) {}
}
