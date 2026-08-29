package android.net;

import java.util.HashSet;
import java.util.Set;

/**
 * JVM stand-in for android.net.NetworkCapabilities.
 *
 * Transport and capability flags are real where the JVM can tell — whether an
 * interface is up, and whether it is wired or wireless by name. Whether a
 * connection is *metered* is not knowable from the JDK on any OS, so it is
 * reported as unmetered (the right default for a desktop on mains power) and
 * declared unavailable so the limitation is documented rather than assumed
 * correct.
 */
public final class NetworkCapabilities {
    public static final int TRANSPORT_CELLULAR = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;

    public static final int NET_CAPABILITY_INTERNET = 12;
    public static final int NET_CAPABILITY_NOT_METERED = 11;
    public static final int NET_CAPABILITY_VALIDATED = 16;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;

    private final Set<Integer> transports = new HashSet<>();
    private final Set<Integer> capabilities = new HashSet<>();

    public NetworkCapabilities addTransportType(int transport) {
        transports.add(transport);
        return this;
    }

    public NetworkCapabilities addCapability(int capability) {
        capabilities.add(capability);
        return this;
    }

    public boolean hasTransport(int transport) { return transports.contains(transport); }

    public boolean hasCapability(int capability) { return capabilities.contains(capability); }
}
