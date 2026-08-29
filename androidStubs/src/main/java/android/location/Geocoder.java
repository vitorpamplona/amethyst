package android.location;

import android.content.Context;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * JVM stand-in for android.location.Geocoder.
 *
 * Reverse geocoding is a real capability on desktop — it is a web service call,
 * not a device feature — but it needs a network client and a provider choice
 * (Nominatim, Photon, a commercial API), which is an application decision, not
 * something a stub jar should make. So it goes through a {@link Backend} the
 * desktop app installs. With none installed, lookups return empty and report a
 * gap rather than pretending the coordinates have no address.
 */
public final class Geocoder {
    public interface Backend {
        List<Address> fromLocation(double latitude, double longitude, int maxResults) throws IOException;

        List<Address> fromLocationName(String name, int maxResults) throws IOException;
    }

    private static volatile Backend backend;

    public static void setBackend(Backend value) { backend = value; }

    /** Android gates the async API on this; desktop's answer depends on the backend. */
    public static boolean isPresent() { return backend != null; }

    private final Locale locale;

    public Geocoder(Context context) { this(context, Locale.getDefault()); }

    public Geocoder(Context context, Locale locale) { this.locale = locale; }

    public List<Address> getFromLocation(double latitude, double longitude, int maxResults) throws IOException {
        Backend installed = backend;
        if (installed == null) {
            PlatformGaps.report(
                    "Geocoder.getFromLocation",
                    "no geocoding backend installed; desktop can reverse-geocode over HTTP (e.g. Nominatim)");
            return Collections.emptyList();
        }
        return installed.fromLocation(latitude, longitude, maxResults);
    }

    public List<Address> getFromLocationName(String locationName, int maxResults) throws IOException {
        Backend installed = backend;
        if (installed == null) {
            PlatformGaps.report("Geocoder.getFromLocationName", "no geocoding backend installed");
            return Collections.emptyList();
        }
        return installed.fromLocationName(locationName, maxResults);
    }
}
