package android.net;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JVM stand-in for android.net.ConnectivityManager, backed by
 * {@link NetworkInterface}.
 *
 * "Am I online" is a real question the JDK can answer, so this answers it
 * rather than reporting a fixed value: an interface that is up, is not the
 * loopback, and has an address means connected, and the interface name
 * distinguishes wireless from wired well enough for the one thing the app does
 * with it. Android pushes changes; the JDK has no such notification, so
 * registered callbacks are driven by a low-frequency poll that only fires on an
 * actual transition.
 */
public class ConnectivityManager {
    private static final long POLL_SECONDS = 5;

    public abstract static class NetworkCallback {
        public void onAvailable(Network network) {}

        public void onLost(Network network) {}

        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {}
    }

    private final List<NetworkCallback> callbacks = new CopyOnWriteArrayList<>();
    private volatile ScheduledExecutorService poller;
    private volatile boolean lastKnownConnected;

    public Network getActiveNetwork() {
        return isConnected() ? new Network(1L) : null;
    }

    public NetworkCapabilities getNetworkCapabilities(Network network) {
        if (network == null || !isConnected()) return null;

        NetworkCapabilities capabilities =
                new NetworkCapabilities()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        // The JDK cannot tell whether a connection is metered on
                        // any OS. Unmetered is the right default for a desktop,
                        // but it is a guess, so it is declared as one.
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        PlatformGaps.unavailable(
                "ConnectivityManager.isMetered",
                "the JDK exposes no metered-connection signal; desktop assumes unmetered");

        return capabilities.addTransportType(activeTransport());
    }

    public void registerDefaultNetworkCallback(NetworkCallback callback) {
        callbacks.add(callback);
        startPolling();
        if (isConnected()) callback.onAvailable(new Network(1L));
    }

    public void registerNetworkCallback(Object request, NetworkCallback callback) {
        registerDefaultNetworkCallback(callback);
    }

    public void unregisterNetworkCallback(NetworkCallback callback) {
        callbacks.remove(callback);
        if (callbacks.isEmpty()) stopPolling();
    }

    /** True when any non-loopback interface is up and has an address. */
    public static boolean isConnected() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nic.isLoopback() || !nic.isUp()) continue;
                if (nic.getInetAddresses().hasMoreElements()) return true;
            }
        } catch (Exception e) {
            // Treating an unreadable interface list as "offline" would make the
            // app stop retrying; assume connected and let requests fail honestly.
            return true;
        }
        return false;
    }

    private static int activeTransport() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nic.isLoopback() || !nic.isUp() || !nic.getInetAddresses().hasMoreElements()) continue;
                String name = (nic.getName() + " " + String.valueOf(nic.getDisplayName())).toLowerCase();
                if (name.contains("wl") || name.contains("wi-fi") || name.contains("wifi") || name.contains("wlan")) {
                    return NetworkCapabilities.TRANSPORT_WIFI;
                }
            }
        } catch (Exception ignored) {
            // fall through to the wired default
        }
        return NetworkCapabilities.TRANSPORT_ETHERNET;
    }

    private synchronized void startPolling() {
        if (poller != null) return;
        lastKnownConnected = isConnected();
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "connectivity-poll");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleWithFixedDelay(this::pollOnce, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void stopPolling() {
        if (poller == null) return;
        poller.shutdownNow();
        poller = null;
    }

    private void pollOnce() {
        boolean connected = isConnected();
        if (connected == lastKnownConnected) return;
        lastKnownConnected = connected;

        Network network = new Network(1L);
        for (NetworkCallback callback : callbacks) {
            if (connected) {
                callback.onAvailable(network);
                callback.onCapabilitiesChanged(network, getNetworkCapabilities(network));
            } else {
                callback.onLost(network);
            }
        }
    }
}
